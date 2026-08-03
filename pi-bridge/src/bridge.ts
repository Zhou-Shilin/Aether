import { AsyncLocalStorage } from "node:async_hooks";
import { createInterface } from "node:readline";
import { stdin as input, stdout as output, stderr } from "node:process";
import {
  getSupportedThinkingLevels,
  clampThinkingLevel,
  createModels,
  createProvider,
  defaultProviderAuthContext,
  fauxAssistantMessage,
  fauxProvider,
  fauxToolCall,
  retryAssistantCall,
  type AuthContext,
  type AuthInteraction,
  type AssistantMessage,
  type AssistantMessageEvent,
  type Context,
  type Credential,
  type CredentialInfo,
  type CredentialStore,
  type ImageContent,
  type Message,
  type Model,
  type OAuthAuth,
  type MutableModels,
  type ProviderStreams,
  type SimpleStreamOptions,
  type TextContent,
  type Usage,
} from "@earendil-works/pi-ai";
import {
  builtinProviders,
  getBuiltinModels,
  getBuiltinProviders,
} from "@earendil-works/pi-ai/providers/all";
import {
  AgentHarness,
  InMemorySessionRepo,
  runAgentLoopContinue,
  type AgentContext,
  type AgentHarnessEvent,
  type AgentLoopConfig,
  type AgentMessage,
  type AgentTool,
  type AgentToolResult,
  type StreamFn,
} from "@earendil-works/pi-agent-core/node";
import {
  createSyntheticSourceInfo,
  type BuildSystemPromptOptions,
  type ExtensionCommandContext,
  type ExtensionEvent,
  type RegisteredTool,
  type ScopedModel,
  type ToolInfo,
} from "@earendil-works/pi-coding-agent";
import { openAICompletionsApi } from "@earendil-works/pi-ai/api/openai-completions.lazy";
import type { TSchema } from "typebox";
import {
  extensionTools,
  installAetherExtensionPackage,
  listAetherExtensionPackages,
  loadAetherExtensions,
  removeAetherExtensionPackage,
  updateAetherExtensionPackage,
  type AetherExtensionRuntime,
} from "./extensions.js";
import {
  aetherAppExtensionSnapshot,
  configureAetherExtensionTransport,
  dispatchAetherAppExtensionEvent,
  invokeAetherAppExtensionAction,
  loadAetherAppExtensions,
} from "./aether-extensions.js";

const BRIDGE_VERSION = "2.0.0-alpha.0";
const PI_AI_VERSION = "0.83.0";
const PI_AGENT_CORE_VERSION = "0.83.0";
const PI_CODING_AGENT_VERSION = "0.83.0";
const AETHER_MANUAL_OAUTH_CALLBACK_HOST = "203.0.113.1";
const OAUTH_FETCH_MAX_ATTEMPTS = 3;
const DEFAULT_HARNESS_SESSION_LIMIT = 8;
const DEFAULT_HARNESS_SESSION_TTL_MS = 30 * 60 * 1000;
const DEFAULT_AGENT_RETRY_MAX_RETRIES = 5;
const DEFAULT_AGENT_RETRY_BASE_DELAY_MS = 2_000;
const CUSTOM_BASE_URL_BUILTIN_PROVIDER_IDS = new Set(["openai", "anthropic"]);

type JsonObject = Record<string, unknown>;

interface BridgeRequest {
  id?: string;
  type?: string;
  payload?: JsonObject;
}

interface ModelConfig {
  provider_type: string;
  provider_config_id: string;
  pi_provider_id: string;
  pi_api: string;
  model_id: string;
  base_url: string;
  api_key?: string;
  custom_headers?: Record<string, string>;
  reasoning?: boolean;
  context_window?: number;
  max_tokens?: number;
  timeout_ms?: number;
  max_retries?: number;
  max_retry_delay_ms?: number;
  auth_method?: "api_key" | "oauth" | "ambient";
  oauth_credential?: JsonObject;
  provider_env?: Record<string, string>;
  faux_response?: string;
  faux_tool_calls?: Array<{ name: string; arguments: JsonObject; id?: string }>;
  faux_tokens_per_second?: number;
}

interface HostToolDefinition {
  name: string;
  description: string;
  parameters?: JsonObject;
  execution_mode?: "sequential" | "parallel";
}

interface PendingHostToolRequest {
  sessionId: string;
  resolve: (result: AgentToolResult<JsonObject>) => void;
  reject: (error: Error) => void;
  onUpdate?: (partialResult: AgentToolResult<JsonObject>) => void;
}

interface PendingAetherHostCall {
  resolve: (result: JsonObject) => void;
  reject: (error: Error) => void;
  timeout: NodeJS.Timeout;
}

type InMemorySession = Awaited<ReturnType<InMemorySessionRepo["create"]>>;

interface HarnessSessionState {
  sessionId: string;
  configSignature: string;
  toolSignature: string;
  workspaceDirectory: string;
  models: MutableModels;
  model: Model<string>;
  credentialStore?: BridgeCredentialStore;
  session: InMemorySession;
  harness: AgentHarness;
  hostTools: AgentTool[];
  extensionRuntime: AetherExtensionRuntime;
  configuredExtensionPaths: string[];
  pendingExtensionRuntime?: AetherExtensionRuntime;
  extensionUnsubscribers: Array<() => void>;
  pendingToolRefresh: boolean;
  pendingActiveToolNames?: string[];
  currentSignal?: AbortSignal;
  systemPrompt: string;
  currentRequestId: string;
  toolArgsById: Map<string, unknown>;
  agentRetryMaxRetries: number;
  lastAccessedAt: number;
}

const activeAborters = new Map<string, () => void | Promise<unknown>>();
const pendingHostToolRequests = new Map<string, PendingHostToolRequest>();
const pendingAetherHostCalls = new Map<string, PendingAetherHostCall>();
const aetherSubscriberRequestIds = new Set<string>();
const aetherOperationContext = new AsyncLocalStorage<string>();
const activeAetherOperationRequestIds = new Set<string>();
const pendingAuthPrompts = new Map<
  string,
  {
    resolve: (value: string) => void;
    reject: (error: Error) => void;
    requestId: string;
  }
>();
const harnessSessions = new Map<string, HarnessSessionState>();
let currentExtensionLoadOptions = {
  disabledExtensionPaths: [] as string[],
  disabledPackageSources: [] as string[],
};

interface SharedCredentialState {
  credential?: Credential;
  queue: Promise<void>;
}

const sharedCredentialStates = new Map<string, SharedCredentialState>();
let oauthTransportQueue: Promise<void> = Promise.resolve();

const builtinProviderById = new Map(
  builtinProviders().map((provider) => {
    const oauth = aetherOAuthAuth(provider.id, provider.auth.oauth);
    return [
      provider.id,
      oauth
        ? {
            ...provider,
            auth: {
              ...provider.auth,
              oauth,
            },
          }
        : provider,
    ];
  }),
);
let defaultModelConfig: ModelConfig | undefined;
let hostToolCounter = 0;
let authPromptCounter = 0;
let aetherHostCallCounter = 0;

function positiveIntegerEnvironmentValue(name: string, fallback: number): number {
  const parsed = Number.parseInt(process.env[name] ?? "", 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

const harnessSessionLimit = positiveIntegerEnvironmentValue(
  "AETHER_PI_MAX_HARNESS_SESSIONS",
  DEFAULT_HARNESS_SESSION_LIMIT,
);
const harnessSessionTtlMs = positiveIntegerEnvironmentValue(
  "AETHER_PI_HARNESS_SESSION_TTL_MS",
  DEFAULT_HARNESS_SESSION_TTL_MS,
);

function aetherOAuthAuth(providerId: string, oauth: OAuthAuth | undefined): OAuthAuth | undefined {
  if (!oauth) return undefined;
  if (providerId !== "openai-codex") return oauth;
  return {
    ...oauth,
    login: async (interaction) =>
      withAetherOAuthTransport(providerId, interaction, () =>
        oauth.login({
          ...interaction,
          notify: (event) => {
            if (event.type === "auth_url") {
              interaction.notify({
                ...event,
                instructions:
                  "Complete login in your browser. When it reaches the localhost redirect, copy the full URL back into Aether.",
              });
              return;
            }
            interaction.notify(event);
          },
        }),
      ),
  };
}

class BridgeCredentialStore implements CredentialStore {
  private readonly state: SharedCredentialState;

  constructor(
    readonly providerId: string,
    providerConfigId: string,
    initialCredential?: Credential,
  ) {
    const existing = sharedCredentialStates.get(providerConfigId);
    if (existing) {
      this.state = existing;
    } else {
      this.state = { credential: initialCredential, queue: Promise.resolve() };
      sharedCredentialStates.set(providerConfigId, this.state);
    }
  }

  async read(providerId: string): Promise<Credential | undefined> {
    if (providerId !== this.providerId) return undefined;
    await this.state.queue;
    return this.state.credential;
  }

  async list(): Promise<readonly CredentialInfo[]> {
    await this.state.queue;
    const credential = this.state.credential;
    return credential ? [{ providerId: this.providerId, type: credential.type }] : [];
  }

  async modify(
    providerId: string,
    fn: (current: Credential | undefined) => Promise<Credential | undefined>,
  ): Promise<Credential | undefined> {
    if (providerId !== this.providerId) return undefined;
    let result: Credential | undefined;
    const operation = this.state.queue.then(async () => {
      const next = await fn(this.state.credential);
      if (next !== undefined) this.state.credential = next;
      result = this.state.credential;
    });
    this.state.queue = operation.catch(() => undefined);
    await operation;
    return result;
  }

  async delete(providerId: string): Promise<void> {
    if (providerId !== this.providerId) return;
    const operation = this.state.queue.then(() => {
      this.state.credential = undefined;
    });
    this.state.queue = operation.catch(() => undefined);
    await operation;
  }
}

async function replaceSharedCredential(
  providerConfigId: string,
  credential: Credential,
): Promise<void> {
  const existing = sharedCredentialStates.get(providerConfigId);
  if (!existing) {
    sharedCredentialStates.set(providerConfigId, {
      credential,
      queue: Promise.resolve(),
    });
    return;
  }
  const operation = existing.queue.then(() => {
    existing.credential = credential;
  });
  existing.queue = operation.catch(() => undefined);
  await operation;
}

async function clearSharedCredential(providerConfigId: string): Promise<boolean> {
  const existing = sharedCredentialStates.get(providerConfigId);
  if (!existing) return false;
  const operation = existing.queue.then(() => {
    existing.credential = undefined;
  });
  existing.queue = operation.catch(() => undefined);
  await operation;
  return true;
}

function writeFrame(frame: JsonObject): void {
  output.write(`${JSON.stringify(frame)}\n`);
}

function writeEvent(id: string, event: string, payload: JsonObject = {}): void {
  writeFrame({ type: "event", id, event, payload });
}

function writeResponse(id: string, payload: JsonObject = {}): void {
  writeFrame({ type: "response", id, ok: true, payload });
}

function writeError(id: string | undefined, error: unknown, code = "bridge_error"): void {
  const message = errorMessageWithCause(error);
  writeFrame({
    type: "error",
    id: id ?? "",
    ok: false,
    error: {
      code,
      message,
    },
  });
}

function emitAetherSubscriberEvent(event: string, payload: JsonObject = {}): void {
  for (const requestId of aetherSubscriberRequestIds) {
    writeEvent(requestId, event, payload);
  }
}

function requestAetherHost(method: string, args: JsonObject): Promise<JsonObject> {
  const operationRequestId = aetherOperationContext.getStore();
  const requestId = operationRequestId &&
      activeAetherOperationRequestIds.has(operationRequestId)
    ? operationRequestId
    : aetherSubscriberRequestIds.values().next().value;
  if (!requestId) {
    throw new Error("The Aether Android host is not subscribed.");
  }
  const callId = `aether-host-${Date.now()}-${++aetherHostCallCounter}`;
  writeEvent(requestId, "aether_host_call", {
    call_id: callId,
    method,
    args,
  });
  return new Promise<JsonObject>((resolve, reject) => {
    const timeout = setTimeout(() => {
      pendingAetherHostCalls.delete(callId);
      reject(new Error(`Aether host call timed out: ${method}`));
    }, 2 * 60 * 1000);
    pendingAetherHostCalls.set(callId, { resolve, reject, timeout });
  });
}

async function runAetherOperation<T>(
  requestId: string,
  operation: () => Promise<T>,
): Promise<T> {
  activeAetherOperationRequestIds.add(requestId);
  try {
    return await aetherOperationContext.run(requestId, operation);
  } finally {
    activeAetherOperationRequestIds.delete(requestId);
  }
}

function resolveAetherHostCall(payload: JsonObject): boolean {
  const callId = asString(payload.call_id).trim();
  const pending = callId ? pendingAetherHostCalls.get(callId) : undefined;
  if (!pending) return false;
  pendingAetherHostCalls.delete(callId);
  clearTimeout(pending.timeout);
  if (asBoolean(payload.ok, true)) {
    pending.resolve(asObject(payload.result));
  } else {
    pending.reject(new Error(asString(payload.error, "Aether host call failed.")));
  }
  return true;
}

configureAetherExtensionTransport({
  requestHost: requestAetherHost,
  invalidate(version) {
    emitAetherSubscriberEvent("aether_invalidated", { version });
  },
  notify(message, level) {
    emitAetherSubscriberEvent("aether_notification", { message, level });
  },
});

function errorMessageWithCause(error: unknown): string {
  if (!(error instanceof Error)) return String(error);
  const messages: string[] = [];
  let current: unknown = error;
  while (current instanceof Error) {
    const message = current.message.trim();
    if (message && !messages.includes(message)) messages.push(message);
    current = current.cause;
  }
  return messages.join(": ") || error.name;
}

function fetchUrl(input: string | URL | Request): string {
  if (typeof input === "string") return input;
  if (input instanceof URL) return input.toString();
  return input.url;
}

async function withAetherOAuthTransport<T>(
  providerId: string,
  interaction: AuthInteraction,
  login: () => Promise<T>,
): Promise<T> {
  if (providerId !== "openai-codex") return login();

  const previousOperation = oauthTransportQueue;
  let releaseTransport: () => void = () => undefined;
  oauthTransportQueue = new Promise<void>((resolve) => {
    releaseTransport = resolve;
  });
  await previousOperation;

  const previousCallbackHost = process.env.PI_OAUTH_CALLBACK_HOST;
  const originalFetch = globalThis.fetch;
  process.env.PI_OAUTH_CALLBACK_HOST = AETHER_MANUAL_OAUTH_CALLBACK_HOST;
  globalThis.fetch = async (input, init) => {
    const url = fetchUrl(input);
    if (!url.startsWith("https://auth.openai.com/")) {
      return originalFetch(input, init);
    }
    let lastError: unknown;
    for (let attempt = 1; attempt <= OAUTH_FETCH_MAX_ATTEMPTS; attempt += 1) {
      try {
        return await originalFetch(input, init);
      } catch (error) {
        if (init?.signal?.aborted) throw error;
        lastError = error;
        if (attempt >= OAUTH_FETCH_MAX_ATTEMPTS) break;
        interaction.notify({
          type: "progress",
          message: `OpenAI login network retry ${attempt + 1}/${OAUTH_FETCH_MAX_ATTEMPTS}.`,
        });
        await new Promise((resolve) => setTimeout(resolve, attempt * 750));
      }
    }
    throw new Error(
      `OpenAI Codex OAuth network request failed after ${OAUTH_FETCH_MAX_ATTEMPTS} attempts`,
      { cause: lastError },
    );
  };

  try {
    return await login();
  } finally {
    globalThis.fetch = originalFetch;
    if (previousCallbackHost === undefined) {
      delete process.env.PI_OAUTH_CALLBACK_HOST;
    } else {
      process.env.PI_OAUTH_CALLBACK_HOST = previousCallbackHost;
    }
    releaseTransport();
  }
}

function asObject(value: unknown): JsonObject {
  return value && typeof value === "object" && !Array.isArray(value) ? (value as JsonObject) : {};
}

function asString(value: unknown, fallback = ""): string {
  return typeof value === "string" ? value : fallback;
}

function asNumber(value: unknown, fallback: number): number {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function asBoolean(value: unknown, fallback: boolean): boolean {
  return typeof value === "boolean" ? value : fallback;
}

function normalizeHeaders(value: unknown): Record<string, string> {
  const inputHeaders = asObject(value);
  const headers: Record<string, string> = {};
  for (const [key, rawValue] of Object.entries(inputHeaders)) {
    if (!key.trim()) continue;
    if (typeof rawValue === "string") headers[key] = rawValue;
  }
  return headers;
}

function normalizeModelConfig(rawValue: unknown): ModelConfig {
  const raw = asObject(rawValue);
  const providerType = asString(raw.provider_type).trim();
  const providerConfigId = asString(raw.provider_config_id).trim();
  const piProviderId = asString(raw.pi_provider_id).trim();
  const piApi = asString(raw.pi_api).trim();
  const modelId = asString(raw.model_id).trim();
  const baseUrl = asString(raw.base_url).trim();
  if (!providerType) throw new Error("model_config.provider_type is required.");
  if (!providerConfigId) throw new Error("model_config.provider_config_id is required.");
  if (!piProviderId) throw new Error("model_config.pi_provider_id is required.");
  if (!piApi) throw new Error("model_config.pi_api is required.");
  if (!modelId) throw new Error("model_config.model_id is required.");
  if (providerType !== "faux" && providerType !== "builtin" && !baseUrl) {
    throw new Error("model_config.base_url is required.");
  }
  const authMethod = asString(raw.auth_method).trim();
  return {
    provider_type: providerType,
    provider_config_id: providerConfigId,
    pi_provider_id: piProviderId,
    pi_api: piApi,
    model_id: modelId,
    base_url: baseUrl,
    api_key: asString(raw.api_key),
    custom_headers: normalizeHeaders(raw.custom_headers),
    reasoning: asBoolean(raw.reasoning, false),
    context_window: asNumber(raw.context_window, 128000),
    max_tokens: asNumber(raw.max_tokens, 16384),
    timeout_ms: asNumber(raw.timeout_ms, 360000),
    max_retries: Math.max(0, asNumber(raw.max_retries, DEFAULT_AGENT_RETRY_MAX_RETRIES)),
    max_retry_delay_ms: asNumber(raw.max_retry_delay_ms, 60000),
    auth_method:
      authMethod === "oauth" || authMethod === "ambient" ? authMethod : "api_key",
    oauth_credential: asObject(raw.oauth_credential),
    provider_env: normalizeHeaders(raw.provider_env),
    faux_response: asString(raw.faux_response),
    faux_tool_calls: normalizeFauxToolCalls(raw.faux_tool_calls),
    faux_tokens_per_second: asNumber(raw.faux_tokens_per_second, 0),
  };
}

function isOAuthCredential(value: JsonObject): value is JsonObject & {
  access: string;
  refresh: string;
  expires: number;
} {
  return (
    typeof value.access === "string" &&
    typeof value.refresh === "string" &&
    typeof value.expires === "number"
  );
}

function credentialForConfig(config: ModelConfig): Credential | undefined {
  if (config.auth_method === "oauth" && isOAuthCredential(config.oauth_credential ?? {})) {
    return {
      type: "oauth",
      ...config.oauth_credential,
    } as Credential;
  }
  if (config.auth_method === "api_key" && (config.api_key || Object.keys(config.provider_env ?? {}).length > 0)) {
    return {
      type: "api_key",
      key: config.api_key || undefined,
      env: config.provider_env,
    };
  }
  return undefined;
}

function authContextFor(config: ModelConfig): AuthContext {
  const fallback = defaultProviderAuthContext();
  const configuredEnv = config.provider_env ?? {};
  return {
    env: async (name) => configuredEnv[name] ?? fallback.env(name),
    fileExists: (path) => fallback.fileExists(path),
  };
}

function normalizeFauxToolCalls(rawValue: unknown): Array<{ name: string; arguments: JsonObject; id?: string }> {
  if (!Array.isArray(rawValue)) return [];
  return rawValue.flatMap((rawCall) => {
    const raw = asObject(rawCall);
    const name = asString(raw.name, asString(raw.tool_name)).trim();
    if (!name) return [];
    return [
      {
        name,
        arguments: asObject(raw.arguments),
        id: asString(raw.id).trim() || undefined,
      },
    ];
  });
}

function apiStreamsFor(piApi: string): ProviderStreams {
  switch (piApi) {
    case "openai-completions":
      return openAICompletionsApi();
    default:
      throw new Error(`Unsupported custom Pi API: ${piApi}`);
  }
}

function createAetherModel(config: ModelConfig): Model<string> {
  return {
    id: config.model_id,
    name: config.model_id,
    api: config.pi_api,
    provider: config.pi_provider_id,
    baseUrl: config.base_url,
    reasoning: config.reasoning ?? false,
    input: ["text", "image"],
    cost: {
      input: 0,
      output: 0,
      cacheRead: 0,
      cacheWrite: 0,
    },
    contextWindow: config.context_window ?? 128000,
    maxTokens: config.max_tokens ?? 16384,
    headers: config.custom_headers,
  };
}

function normalizedBaseUrl(value: string | undefined): string {
  return (value ?? "").trim().replace(/\/+$/, "");
}

function buildModels(config: ModelConfig): {
  models: MutableModels;
  model: Model<string>;
  credentialStore?: BridgeCredentialStore;
} {
  if (config.provider_type === "faux") {
    const models = createModels();
    const faux = fauxProvider({
      provider: config.pi_provider_id,
      models: [
        {
          id: config.model_id,
          reasoning: config.reasoning ?? true,
          input: ["text", "image"],
          contextWindow: config.context_window ?? 128000,
          maxTokens: config.max_tokens ?? 16384,
        },
      ],
      tokensPerSecond: config.faux_tokens_per_second ?? 0,
    });
    if (config.faux_tool_calls && config.faux_tool_calls.length > 0) {
      faux.setResponses([
        fauxAssistantMessage(
          config.faux_tool_calls.map((toolCall) =>
            fauxToolCall(toolCall.name, toolCall.arguments, toolCall.id ? { id: toolCall.id } : undefined),
          ),
          { stopReason: "toolUse" },
        ),
        ...Array.from({ length: 32 }, () => fauxAssistantMessage(config.faux_response || "ok")),
      ]);
    } else {
      faux.setResponses(
        Array.from({ length: 32 }, () => fauxAssistantMessage(config.faux_response || "ok")),
      );
    }
    models.setProvider(faux.provider);
    const model = faux.getModel(config.model_id) ?? faux.getModel();
    return { models, model };
  }

  if (config.provider_type === "builtin") {
    const provider = builtinProviderById.get(config.pi_provider_id);
    if (!provider) throw new Error(`Unknown built-in Pi provider: ${config.pi_provider_id}`);
    const providerModels = provider.getModels();
    const builtinModel = providerModels.find((candidate) => candidate.id === config.model_id);
    const providerBaseUrl = provider.baseUrl ?? providerModels[0]?.baseUrl;
    const usesCustomBaseUrl =
      CUSTOM_BASE_URL_BUILTIN_PROVIDER_IDS.has(provider.id) &&
      normalizedBaseUrl(config.base_url) !== normalizedBaseUrl(providerBaseUrl);
    const modelTemplate = builtinModel ?? (usesCustomBaseUrl ? providerModels[0] : undefined);
    if (!modelTemplate) {
      throw new Error(
        `Unknown model ${config.model_id} for built-in Pi provider ${config.pi_provider_id}.`,
      );
    }
    const credentialStore = new BridgeCredentialStore(
      provider.id,
      config.provider_config_id,
      credentialForConfig(config),
    );
    const models = createModels({
      credentials: credentialStore,
      authContext: authContextFor(config),
    });
    models.setProvider(provider);
    const model = {
      ...modelTemplate,
      ...(builtinModel
        ? {}
        : {
            id: config.model_id,
            name: config.model_id,
            reasoning: config.reasoning ?? false,
            contextWindow: config.context_window ?? 128000,
            maxTokens: config.max_tokens ?? 16384,
            cost: {
              input: 0,
              output: 0,
              cacheRead: 0,
              cacheWrite: 0,
            },
          }),
      ...(config.base_url ? { baseUrl: config.base_url } : {}),
      headers: {
        ...modelTemplate.headers,
        ...config.custom_headers,
      },
    } as Model<string>;
    return { models, model, credentialStore };
  }

  const models = createModels();
  const model = createAetherModel(config);
  const headers = config.custom_headers ?? {};
  const provider = createProvider({
    id: config.pi_provider_id,
    name: config.pi_provider_id,
    baseUrl: config.base_url,
    headers,
    auth: {
      apiKey: {
        name: "Aether provider credentials",
        resolve: async () => ({
          auth: {
            apiKey: config.api_key || undefined,
            baseUrl: config.base_url || undefined,
            headers,
          },
          source: "Aether",
        }),
      },
    },
    models: [model],
    api: apiStreamsFor(config.pi_api),
  });
  models.setProvider(provider);
  return { models, model };
}

async function credentialPayload(
  credentialStore: BridgeCredentialStore | undefined,
): Promise<JsonObject> {
  if (!credentialStore) return {};
  const credential = await credentialStore.read(credentialStore.providerId);
  if (!credential || credential.type !== "oauth") return {};
  return {
    oauth_credential: credential as unknown as JsonObject,
  };
}

function providerCatalogPayload(): JsonObject {
  return {
    providers: getBuiltinProviders().map((providerId) => {
      const provider = builtinProviderById.get(providerId);
      const models = getBuiltinModels(providerId);
      return {
        id: providerId,
        name: provider?.name ?? providerId,
        base_url: provider?.baseUrl ?? "",
        auth: {
          api_key: provider?.auth.apiKey?.name ?? "",
          api_key_login: Boolean(provider?.auth.apiKey?.login),
          oauth: provider?.auth.oauth?.name ?? "",
          ambient: Boolean(provider?.auth.apiKey && !provider.auth.apiKey.login),
        },
        models: models.map((model) => ({
          id: model.id,
          name: model.name,
          api: model.api,
          reasoning: model.reasoning,
          thinking_levels: getSupportedThinkingLevels(model),
          thinking_level_clamps: Object.fromEntries(
            ["off", "minimal", "low", "medium", "high", "xhigh", "max"].map((level) => [
              level,
              clampThinkingLevel(model, level as "off" | "minimal" | "low" | "medium" | "high" | "xhigh" | "max"),
            ]),
          ),
          input: model.input,
          context_window: model.contextWindow,
          max_tokens: model.maxTokens,
        })),
      };
    }),
  };
}

function normalizeContentPart(rawValue: unknown): { type: "text"; text: string } | { type: "image"; mimeType: string; data: string } | undefined {
  const raw = asObject(rawValue);
  const type = asString(raw.type);
  if (type === "image") {
    const data = asString(raw.data).trim();
    if (!data) return undefined;
    return {
      type: "image",
      mimeType: asString(raw.mime_type, asString(raw.mimeType, "application/octet-stream")),
      data,
    };
  }
  const text = asString(raw.text);
  return { type: "text", text };
}

function normalizeUserContent(rawContent: unknown): string | Array<{ type: "text"; text: string } | { type: "image"; mimeType: string; data: string }> {
  if (typeof rawContent === "string") return rawContent;
  if (!Array.isArray(rawContent)) return "";
  const parts = rawContent.map(normalizeContentPart).filter((part): part is NonNullable<typeof part> => Boolean(part));
  if (parts.length === 1 && parts[0].type === "text") return parts[0].text;
  return parts;
}

function textFromContent(rawContent: unknown): string {
  if (typeof rawContent === "string") return rawContent;
  if (!Array.isArray(rawContent)) return "";
  return rawContent
    .map((part) => {
      const raw = asObject(part);
      return asString(raw.type) === "text" ? asString(raw.text) : "";
    })
    .filter(Boolean)
    .join("");
}

function persistedPiAssistantMessage(rawProviderPayload: unknown): JsonObject {
  const providerPayload = asObject(rawProviderPayload);
  const wrapped = asObject(
    providerPayload.piAssistantMessage ??
      providerPayload.pi_assistant_message ??
      providerPayload.assistant_message,
  );
  if (wrapped.role === "assistant" && Array.isArray(wrapped.content)) return wrapped;
  if (providerPayload.role === "assistant" && Array.isArray(providerPayload.content)) {
    return providerPayload;
  }
  return {};
}

function normalizeMessages(rawMessages: unknown): Context["messages"] {
  if (!Array.isArray(rawMessages)) return [];
  return rawMessages.flatMap((rawMessage): Message[] => {
    const raw = asObject(rawMessage);
    const role = asString(raw.role);
    if (role === "assistant") {
      const persistedAssistant = persistedPiAssistantMessage(raw.provider_payload);
      if (persistedAssistant.role === "assistant" && Array.isArray(persistedAssistant.content)) {
        return [persistedAssistant as unknown as AssistantMessage];
      }
      return [
        {
          role: "assistant" as const,
          content: [{ type: "text" as const, text: asString(raw.text, textFromContent(raw.content)) }],
          api: asString(raw.api, "aether"),
          provider: asString(raw.provider, "aether"),
          model: asString(raw.model, "unknown"),
          usage: emptyUsage(),
          stopReason: "stop" as const,
          timestamp: asNumber(raw.timestamp, Date.now()),
        },
      ];
    }
    if (role === "toolResult") {
      return [
        {
          role: "toolResult" as const,
          toolCallId: asString(raw.tool_call_id, asString(raw.toolCallId)),
          toolName: asString(raw.tool_name, asString(raw.toolName)),
          content: [{ type: "text" as const, text: asString(raw.text, asString(raw.content)) }],
          isError: asBoolean(raw.is_error, asBoolean(raw.isError, false)),
          timestamp: asNumber(raw.timestamp, Date.now()),
        },
      ];
    }
    return [
      {
        role: "user" as const,
        content: normalizeUserContent(raw.content ?? raw.text),
        timestamp: asNumber(raw.timestamp, Date.now()),
      },
    ];
  });
}

function buildContext(payload: JsonObject): Context {
  return {
    systemPrompt: asString(payload.system_prompt),
    messages: normalizeMessages(payload.messages),
  };
}

function emptyUsage(): Usage {
  return {
    input: 0,
    output: 0,
    cacheRead: 0,
    cacheWrite: 0,
    totalTokens: 0,
    cost: {
      input: 0,
      output: 0,
      cacheRead: 0,
      cacheWrite: 0,
      total: 0,
    },
  };
}

function usagePayload(usage: Usage | undefined): JsonObject {
  if (!usage) return {};
  return {
    input_tokens: usage.input,
    output_tokens: usage.output,
    total_tokens: usage.totalTokens,
    reasoning_tokens: usage.reasoning,
    cached_input_tokens: usage.cacheRead,
  };
}

function assistantText(message: AssistantMessage): string {
  return message.content
    .filter((block) => block.type === "text")
    .map((block) => block.text)
    .join("");
}

function assistantThinking(message: AssistantMessage): string {
  return message.content
    .filter((block) => block.type === "thinking")
    .map((block) => block.thinking)
    .join("");
}

function assistantPayload(message: AssistantMessage): JsonObject {
  return {
    assistant_text: assistantText(message),
    reasoning_text: assistantThinking(message),
    assistant_message: message as unknown as JsonObject,
    usage: usagePayload(message.usage),
    provider: message.provider,
    model: message.model,
    response_id: message.responseId,
    stop_reason: message.stopReason,
    error_message: message.errorMessage,
  };
}

function emitStreamEvent(requestId: string, event: AssistantMessageEvent): void {
  switch (event.type) {
    case "text_delta":
      writeEvent(requestId, "assistant_text_delta", { delta: event.delta });
      break;
    case "thinking_delta":
      writeEvent(requestId, "assistant_reasoning_delta", { delta: event.delta });
      break;
    case "toolcall_start":
      writeEvent(requestId, "tool_call_start", { content_index: event.contentIndex });
      break;
    case "toolcall_delta":
      writeEvent(requestId, "tool_call_delta", { content_index: event.contentIndex, delta: event.delta });
      break;
    case "toolcall_end":
      writeEvent(requestId, "tool_call_end", {
        id: event.toolCall.id,
        name: event.toolCall.name,
        arguments: event.toolCall.arguments,
      });
      break;
    case "done":
      writeEvent(requestId, "assistant_done", assistantPayload(event.message));
      break;
    case "error":
      writeEvent(requestId, "assistant_error", assistantPayload(event.error));
      break;
  }
}

function streamOptionsFor(
  payload: JsonObject,
  signal: AbortSignal,
  config: ModelConfig,
  model: Model<string>,
): SimpleStreamOptions {
  const options: SimpleStreamOptions = {
    signal,
    sessionId: asString(payload.session_id),
    headers: normalizeHeaders(payload.headers),
    timeoutMs: asNumber(payload.timeout_ms, config.timeout_ms ?? 360000),
    maxRetries: asNumber(payload.max_retries, config.max_retries ?? 5),
    maxRetryDelayMs: asNumber(payload.max_retry_delay_ms, config.max_retry_delay_ms ?? 60000),
  };
  const temperature = payload.temperature;
  if (typeof temperature === "number") options.temperature = temperature;
  const maxTokens = payload.max_tokens;
  if (typeof maxTokens === "number") options.maxTokens = maxTokens;
  const thinkingLevel = thinkingLevelFor(payload);
  const reasoning = thinkingLevel ? clampThinkingLevel(model, thinkingLevel) : undefined;
  if (reasoning && reasoning !== "off") {
    options.reasoning = reasoning as SimpleStreamOptions["reasoning"];
  }
  return options;
}

function harnessStreamOptions(payload: JsonObject, config: ModelConfig) {
  return {
    headers: normalizeHeaders(payload.headers),
    timeoutMs: asNumber(payload.timeout_ms, config.timeout_ms ?? 360000),
    // Retry failed harness turns after rewinding their session state.
    maxRetries: 0,
    maxRetryDelayMs: asNumber(payload.max_retry_delay_ms, config.max_retry_delay_ms ?? 60000),
  };
}

function normalizeHostToolDefinitions(rawTools: unknown): HostToolDefinition[] {
  if (!Array.isArray(rawTools)) return [];
  return rawTools
    .map((rawTool): HostToolDefinition | undefined => {
      const raw = asObject(rawTool);
      const name = asString(raw.name).trim();
      if (!name) return undefined;
      const executionMode = asString(raw.execution_mode, asString(raw.executionMode)).trim();
      return {
        name,
        description: asString(raw.description),
        parameters: asObject(raw.parameters),
        execution_mode: executionMode === "sequential" ? "sequential" : "parallel",
      };
    })
    .filter((tool): tool is HostToolDefinition => Boolean(tool));
}

function hostToolSchema(definition: HostToolDefinition): TSchema {
  const schema = asObject(definition.parameters);
  if (asString(schema.type)) return schema as unknown as TSchema;
  return {
    type: "object",
    properties: {},
    additionalProperties: true,
  } as unknown as TSchema;
}

function normalizeToolArguments(args: unknown): JsonObject {
  if (typeof args === "string") {
    try {
      return asObject(JSON.parse(args));
    } catch {
      return {};
    }
  }
  return asObject(args);
}

function normalizeToolContent(rawContent: unknown, fallbackText: string): Array<TextContent | ImageContent> {
  if (!Array.isArray(rawContent)) return [{ type: "text", text: fallbackText }];
  const content = rawContent
    .map((rawPart): TextContent | ImageContent | undefined => {
      const part = asObject(rawPart);
      const type = asString(part.type);
      if (type === "image") {
        const data = asString(part.data).trim();
        if (!data) return undefined;
        return {
          type: "image",
          mimeType: asString(part.mime_type, asString(part.mimeType, "application/octet-stream")),
          data,
        };
      }
      if (type === "text") {
        return { type: "text", text: asString(part.text) };
      }
      return undefined;
    })
    .filter((part): part is TextContent | ImageContent => Boolean(part));
  return content.length > 0 ? content : [{ type: "text", text: fallbackText }];
}

function hostToolResultFromPayload(payload: JsonObject): AgentToolResult<JsonObject> {
  const outputText = asString(payload.output_json, asString(payload.output, ""));
  const details = {
    ...asObject(payload.details),
    tool_request_id: asString(payload.tool_request_id),
    tool_call_id: asString(payload.tool_call_id),
    tool_name: asString(payload.tool_name),
    arguments_json: asString(payload.arguments_json),
    output_json: outputText,
    raw_output_json: asString(payload.raw_output_json),
    is_error: asBoolean(payload.is_error, false),
  };
  return {
    content: normalizeToolContent(payload.content, outputText),
    details,
    terminate: asBoolean(payload.terminate, false) || undefined,
  };
}

function resolveHostToolResult(payload: JsonObject): boolean {
  const sessionId = asString(payload.session_id).trim();
  const systemPrompt = asString(payload.system_prompt);
  if (sessionId && systemPrompt) {
    const state = harnessSessions.get(sessionId);
    if (state) {
      state.systemPrompt = systemPrompt;
      state.lastAccessedAt = Date.now();
    }
  }
  const toolRequestId = asString(payload.tool_request_id).trim();
  const pending = toolRequestId ? pendingHostToolRequests.get(toolRequestId) : undefined;
  if (!pending) return false;
  pendingHostToolRequests.delete(toolRequestId);
  pending.resolve(hostToolResultFromPayload(payload));
  return true;
}

function applyHostToolProgress(payload: JsonObject): boolean {
  const toolRequestId = asString(payload.tool_request_id).trim();
  const pending = toolRequestId ? pendingHostToolRequests.get(toolRequestId) : undefined;
  if (!pending) return false;
  pending.onUpdate?.(hostToolResultFromPayload(payload));
  return true;
}

function requestHostTool(
  runRequestId: string,
  sessionId: string,
  toolName: string,
  toolCallId: string,
  params: JsonObject,
  executionMode: "sequential" | "parallel",
  signal?: AbortSignal,
  onUpdate?: (partialResult: AgentToolResult<JsonObject>) => void,
): Promise<AgentToolResult<JsonObject>> {
  const toolRequestId = `host-tool-${Date.now()}-${++hostToolCounter}`;
  const argumentsJson = JSON.stringify(params);
  writeEvent(runRequestId, "host_tool_request", {
    tool_request_id: toolRequestId,
    session_id: sessionId,
    tool_call_id: toolCallId,
    tool_name: toolName,
    arguments: params,
    arguments_json: argumentsJson,
    execution_mode: executionMode,
  });
  return new Promise<AgentToolResult<JsonObject>>((resolve, reject) => {
    if (signal?.aborted) {
      reject(new Error("Host tool execution aborted."));
      return;
    }
    const abortListener = () => {
      pendingHostToolRequests.delete(toolRequestId);
      reject(new Error("Host tool execution aborted."));
    };
    signal?.addEventListener("abort", abortListener, { once: true });
    pendingHostToolRequests.set(toolRequestId, {
      sessionId,
      resolve: (result) => {
        signal?.removeEventListener("abort", abortListener);
        resolve(result);
      },
      reject: (error) => {
        signal?.removeEventListener("abort", abortListener);
        reject(error);
      },
      onUpdate,
    });
  });
}

function createHostTool(state: HarnessSessionState, definition: HostToolDefinition): AgentTool<TSchema, JsonObject> {
  return {
    label: definition.name,
    name: definition.name,
    description: definition.description,
    parameters: hostToolSchema(definition),
    prepareArguments: normalizeToolArguments,
    executionMode: definition.execution_mode,
    execute: async (toolCallId, params, signal, onUpdate) => {
      if (!state.currentRequestId) {
        throw new Error(`Host tool ${definition.name} was called without an active Pi request.`);
      }
      return requestHostTool(
        state.currentRequestId,
        state.sessionId,
        definition.name,
        toolCallId,
        normalizeToolArguments(params),
        definition.execution_mode ?? "parallel",
        signal,
        onUpdate,
      );
    },
  };
}

function extensionMessageText(content: unknown): string {
  if (typeof content === "string") return content;
  if (!Array.isArray(content)) return "";
  return content
    .flatMap((part) => {
      const value = asObject(part);
      return value.type === "text" ? [asString(value.text)] : [];
    })
    .join("\n");
}

function allSessionTools(state: HarnessSessionState): AgentTool[] {
  const tools = new Map(state.hostTools.map((tool) => [tool.name, tool]));
  for (const tool of extensionTools(state.extensionRuntime)) {
    tools.set(tool.name, tool);
  }
  return [...tools.values()];
}

function allSessionToolInfo(state: HarnessSessionState): ToolInfo[] {
  const tools = new Map<string, ToolInfo>();
  for (const tool of state.hostTools) {
    tools.set(tool.name, {
      name: tool.name,
      description: tool.description,
      parameters: tool.parameters,
      sourceInfo: createSyntheticSourceInfo(`<aether:${tool.name}>`, {
        source: "sdk",
      }),
    });
  }
  for (const registered of state.extensionRuntime.runner.getAllRegisteredTools()) {
    const definition = registered.definition;
    tools.set(definition.name, {
      name: definition.name,
      description: definition.description,
      parameters: definition.parameters,
      promptGuidelines: definition.promptGuidelines,
      sourceInfo: registered.sourceInfo,
    });
  }
  return [...tools.values()];
}

function sessionCommands(state: HarnessSessionState) {
  return state.extensionRuntime.runner.getRegisteredCommands().map((command) => ({
    name: command.invocationName,
    description: command.description,
    source: "extension" as const,
    sourceInfo: command.sourceInfo,
  }));
}

async function refreshSessionTools(
  state: HarnessSessionState,
  requestedActiveToolNames?: string[],
): Promise<void> {
  const previousToolNames = new Set(state.harness.getTools().map((tool) => tool.name));
  const previousActiveNames =
    requestedActiveToolNames ?? state.harness.getActiveTools().map((tool) => tool.name);
  const tools = allSessionTools(state);
  const toolNames = new Set(tools.map((tool) => tool.name));
  const activeNames = previousActiveNames.filter((name) => toolNames.has(name));
  for (const tool of tools) {
    if (!previousToolNames.has(tool.name)) activeNames.push(tool.name);
  }
  await state.harness.setTools(tools, [...new Set(activeNames)]);
}

function queueExtensionUserMessage(
  state: HarnessSessionState,
  content: string | Array<TextContent | ImageContent>,
  deliverAs?: "steer" | "followUp",
): void {
  const text = extensionMessageText(content);
  const images = Array.isArray(content)
    ? content.filter((part): part is ImageContent => part.type === "image")
    : [];
  if (!text && images.length === 0) return;
  const options = images.length > 0 ? { images } : undefined;
  if (state.currentRequestId) {
    const operation =
      deliverAs === "steer"
        ? state.harness.steer(text, options)
        : state.harness.followUp(text, options);
    void operation.catch((error) => {
      stderr.write(`pi extension message failed: ${errorMessageWithCause(error)}\n`);
    });
    return;
  }
  void state.harness.nextTurn(text, options).catch((error) => {
    stderr.write(`pi extension message failed: ${errorMessageWithCause(error)}\n`);
  });
}

function extensionSystemPromptOptions(state: HarnessSessionState): BuildSystemPromptOptions {
  return {
    cwd: state.workspaceDirectory,
  } as BuildSystemPromptOptions;
}

function bindExtensionCore(state: HarnessSessionState): void {
  const runner = state.extensionRuntime.runner;
  runner.bindCore(
    {
      sendMessage: (message, options) => {
        state.extensionRuntime.sessionManager.appendCustomMessageEntry(
          message.customType,
          message.content,
          message.display,
          message.details,
        );
        if (options?.triggerTurn || options?.deliverAs) {
          queueExtensionUserMessage(
            state,
            message.content,
            options.deliverAs === "steer" ? "steer" : "followUp",
          );
        }
      },
      sendUserMessage: (content, options) => {
        queueExtensionUserMessage(state, content, options?.deliverAs);
      },
      appendEntry: (customType, data) => {
        state.extensionRuntime.sessionManager.appendCustomEntry(customType, data);
      },
      setSessionName: (name) => {
        state.extensionRuntime.sessionManager.appendSessionInfo(name);
        void runner.emit({
          type: "session_info_changed",
          name: state.extensionRuntime.sessionManager.getSessionName(),
        });
      },
      getSessionName: () => state.extensionRuntime.sessionManager.getSessionName(),
      setLabel: (entryId, label) => {
        state.extensionRuntime.sessionManager.appendLabelChange(entryId, label);
      },
      getActiveTools: () => state.harness.getActiveTools().map((tool) => tool.name),
      getAllTools: () => allSessionToolInfo(state),
      setActiveTools: (toolNames) => {
        if (state.currentRequestId) {
          state.pendingActiveToolNames = [...toolNames];
          return;
        }
        void state.harness.setActiveTools(toolNames).catch((error) => {
          runner.emitError({
            extensionPath: "<aether>",
            event: "set_active_tools",
            error: errorMessageWithCause(error),
          });
        });
      },
      refreshTools: () => {
        if (state.currentRequestId) {
          state.pendingToolRefresh = true;
          return;
        }
        void refreshSessionTools(state).catch((error) => {
          runner.emitError({
            extensionPath: "<aether>",
            event: "refresh_tools",
            error: errorMessageWithCause(error),
          });
        });
      },
      getCommands: () => sessionCommands(state),
      setModel: async (model) => {
        const available = state.models.getModel(model.provider, model.id);
        if (!available) return false;
        await state.harness.setModel(available);
        state.model = available;
        return true;
      },
      getThinkingLevel: () => state.harness.getThinkingLevel(),
      setThinkingLevel: (level) => {
        void state.harness.setThinkingLevel(level).catch((error) => {
          runner.emitError({
            extensionPath: "<aether>",
            event: "set_thinking_level",
            error: errorMessageWithCause(error),
          });
        });
      },
    },
    {
      getModel: () => state.harness.getModel(),
      getScopedModels: (): readonly ScopedModel[] => [],
      isIdle: () => !state.currentRequestId,
      isProjectTrusted: () => true,
      getSignal: () => state.currentSignal,
      abort: () => {
        void state.harness.abort();
      },
      hasPendingMessages: () => false,
      shutdown: () => {
        void closeHarnessSession(state.sessionId, state);
      },
      getContextUsage: () => ({
        tokens: null,
        contextWindow: state.harness.getModel().contextWindow,
        percent: null,
      }),
      compact: (options) => {
        void state.harness
          .compact(options?.customInstructions)
          .then((result) => {
            if (!result.firstKeptEntryId) {
              throw new Error("Compaction finished without a first kept entry id.");
            }
            options?.onComplete?.({
              summary: result.summary,
              firstKeptEntryId: result.firstKeptEntryId,
              tokensBefore: result.tokensBefore,
              usage: result.usage,
              details: result.details,
            });
          })
          .catch((error) =>
            options?.onError?.(error instanceof Error ? error : new Error(String(error))),
          );
      },
      getSystemPrompt: () => state.systemPrompt,
      getSystemPromptOptions: () => extensionSystemPromptOptions(state),
    },
  );
  runner.bindCommandContext({
    waitForIdle: () => state.harness.waitForIdle(),
    newSession: async () => ({ cancelled: true }),
    fork: async () => ({ cancelled: true }),
    navigateTree: async (targetId, options) => {
      const result = await state.harness.navigateTree(targetId, options);
      return { cancelled: result.cancelled };
    },
    switchSession: async () => ({ cancelled: true }),
    reload: async () => {
      await reloadExtensionsForState(state);
    },
  });
}

function installExtensionHooks(state: HarnessSessionState): void {
  const runner = state.extensionRuntime.runner;
  state.extensionUnsubscribers.push(
    runner.onError((error) => {
      stderr.write(
        `pi extension error (${error.extensionPath}, ${error.event}): ${error.error}\n`,
      );
      if (state.currentRequestId) {
        writeEvent(state.currentRequestId, "extension_error", {
          extension_path: error.extensionPath,
          event: error.event,
          error: error.error,
        });
      }
    }),
    state.harness.on("before_agent_start", async (event) => {
      const result = await runner.emitBeforeAgentStart(
        event.prompt,
        event.images,
        event.systemPrompt,
        extensionSystemPromptOptions(state),
      );
      return result
        ? {
            messages: result.messages as AgentMessage[] | undefined,
            systemPrompt: result.systemPrompt,
          }
        : undefined;
    }),
    state.harness.on("context", async (event) => ({
      messages: await runner.emitContext(event.messages),
    })),
    state.harness.on("before_provider_request", async (event) => {
      const headers = await runner.emitBeforeProviderHeaders({
        ...(event.streamOptions.headers ?? {}),
      });
      return {
        streamOptions: {
          headers: Object.fromEntries(
            Object.entries(headers).map(([name, value]) => [
              name,
              value === null ? undefined : value,
            ]),
          ),
        },
      };
    }),
    state.harness.on("before_provider_payload", async (event) => ({
      payload: await runner.emitBeforeProviderRequest(event.payload),
    })),
    state.harness.on("tool_call", async (event) =>
      runner.emitToolCall({
        type: "tool_call",
        toolCallId: event.toolCallId,
        toolName: event.toolName,
        input: event.input,
      }),
    ),
    state.harness.on("tool_result", async (event) =>
      runner.emitToolResult({
        type: "tool_result",
        toolCallId: event.toolCallId,
        toolName: event.toolName,
        input: event.input,
        content: event.content,
        details: event.details,
        isError: event.isError,
      }),
    ),
    state.harness.on("session_before_compact", async (event) =>
      runner.emit({
        type: "session_before_compact",
        preparation: event.preparation as never,
        branchEntries: event.branchEntries as never,
        customInstructions: event.customInstructions,
        reason: "manual",
        willRetry: false,
        signal: event.signal,
      }),
    ),
    state.harness.on("session_before_tree", async (event) =>
      runner.emit({
        type: "session_before_tree",
        preparation: event.preparation as never,
        signal: event.signal,
      }),
    ),
    state.harness.subscribe(async (event, signal) => {
      state.currentSignal = signal;
      await emitExtensionHarnessEvent(state, event);
      if (event.type === "settled") {
        state.currentSignal = undefined;
      }
    }),
  );
}

async function emitExtensionHarnessEvent(
  state: HarnessSessionState,
  event: AgentHarnessEvent,
): Promise<void> {
  const runner = state.extensionRuntime.runner;
  switch (event.type) {
    case "agent_start":
    case "agent_end":
    case "turn_start":
    case "turn_end":
    case "message_start":
    case "message_update":
    case "tool_execution_start":
    case "tool_execution_update":
    case "tool_execution_end":
      await runner.emit(event as never);
      return;
    case "message_end": {
      state.extensionRuntime.sessionManager.appendMessage(event.message as never);
      await runner.emitMessageEnd(event);
      return;
    }
    case "settled":
      await runner.emit({ type: "agent_settled" });
      return;
    case "after_provider_response":
      await runner.emit(event);
      return;
    case "model_update":
      await runner.emit({
        type: "model_select",
        model: event.model,
        previousModel: event.previousModel,
        source: event.source,
      });
      return;
    case "thinking_level_update":
      await runner.emit({
        type: "thinking_level_select",
        level: event.level,
        previousLevel: event.previousLevel,
      });
      return;
    case "session_compact":
      await runner.emit({
        type: "session_compact",
        compactionEntry: event.compactionEntry as never,
        fromExtension: event.fromHook,
        reason: "manual",
        willRetry: false,
      });
      return;
    case "session_tree":
      await runner.emit({
        type: "session_tree",
        newLeafId: event.newLeafId,
        oldLeafId: event.oldLeafId,
        summaryEntry: event.summaryEntry as never,
        fromExtension: event.fromHook,
      });
      return;
    default:
      return;
  }
}

function disposeExtensionRuntime(
  state: HarnessSessionState,
  reason: "quit" | "reload",
): Promise<void> {
  const runner = state.extensionRuntime.runner;
  return runner
    .emit({ type: "session_shutdown", reason })
    .catch((error) => {
      stderr.write(`pi extension shutdown failed: ${errorMessageWithCause(error)}\n`);
    })
    .then(() => {
      for (const unsubscribe of state.extensionUnsubscribers.splice(0)) unsubscribe();
      runner.invalidate();
    });
}

async function activateExtensionRuntime(
  state: HarnessSessionState,
  nextRuntime: AetherExtensionRuntime,
  reason: "startup" | "reload",
): Promise<void> {
  if (reason === "reload") await disposeExtensionRuntime(state, "reload");
  state.extensionRuntime = nextRuntime;
  bindExtensionCore(state);
  installExtensionHooks(state);
  await refreshSessionTools(state);
  await nextRuntime.runner.emit({ type: "session_start", reason });
}

async function reloadExtensionsForState(
  state: HarnessSessionState,
  configuredPaths: string[] = [],
  loadOptions: {
    disabledExtensionPaths?: string[];
    disabledPackageSources?: string[];
  } = {},
): Promise<{
  reloaded: boolean;
  scheduled: boolean;
  paths: string[];
  errors: Array<{ path: string; error: string }>;
}> {
  const candidate = await loadAetherExtensions(
    state.workspaceDirectory,
    configuredPaths,
    loadOptions,
  );
  if (candidate.errors.length > 0) {
    candidate.runner.invalidate("Extension reload candidate was rejected.");
    return {
      reloaded: false,
      scheduled: false,
      paths: candidate.paths,
      errors: candidate.errors,
    };
  }
  if (state.currentRequestId) {
    state.pendingExtensionRuntime?.runner.invalidate("Superseded by a newer reload.");
    state.pendingExtensionRuntime = candidate;
    return {
      reloaded: false,
      scheduled: true,
      paths: candidate.paths,
      errors: [],
    };
  }
  await activateExtensionRuntime(state, candidate, "reload");
  return {
    reloaded: true,
    scheduled: false,
    paths: candidate.paths,
    errors: [],
  };
}

function extensionLoadOptionsFromPayload(payload: JsonObject): {
  disabledExtensionPaths: string[];
  disabledPackageSources: string[];
} {
  const stringsFrom = (value: unknown): string[] =>
    Array.isArray(value)
      ? value.filter((entry): entry is string => typeof entry === "string")
      : [];
  const hasOptions =
    Object.prototype.hasOwnProperty.call(payload, "disabled_extension_paths") ||
    Object.prototype.hasOwnProperty.call(payload, "disabled_package_sources");
  if (!hasOptions) return currentExtensionLoadOptions;
  currentExtensionLoadOptions = {
    disabledExtensionPaths: stringsFrom(payload.disabled_extension_paths),
    disabledPackageSources: stringsFrom(payload.disabled_package_sources),
  };
  return currentExtensionLoadOptions;
}

async function applyPendingExtensionChanges(state: HarnessSessionState): Promise<void> {
  const pending = state.pendingExtensionRuntime;
  if (pending) {
    state.pendingExtensionRuntime = undefined;
    await activateExtensionRuntime(state, pending, "reload");
  } else if (state.pendingToolRefresh || state.pendingActiveToolNames) {
    const activeToolNames = state.pendingActiveToolNames;
    state.pendingToolRefresh = false;
    state.pendingActiveToolNames = undefined;
    await refreshSessionTools(state, activeToolNames);
  }
}

function toolTextOutput(result: AgentToolResult<JsonObject> | undefined): string {
  if (!result) return "";
  return result.content
    .filter((part): part is TextContent => part.type === "text")
    .map((part) => part.text)
    .join("");
}

function toolEventPayload(
  toolCallId: string,
  toolName: string,
  args: unknown,
  result?: AgentToolResult<JsonObject>,
  isError?: boolean,
): JsonObject {
  const argsObject = normalizeToolArguments(args);
  const details = asObject(result?.details);
  return {
    id: toolCallId,
    name: toolName,
    arguments: argsObject,
    arguments_json: JSON.stringify(argsObject),
    output_json: asString(details.output_json, toolTextOutput(result)),
    raw_output_json: asString(details.raw_output_json),
    content: result?.content ?? [],
    details,
    is_error: isError ?? asBoolean(details.is_error, false),
  };
}

function promptFromLastUserMessage(messages: Message[]): {
  history: AgentMessage[];
  text: string;
  images: ImageContent[];
} {
  const last = messages[messages.length - 1];
  if (!last || last.role !== "user") {
    return { history: messages as AgentMessage[], text: "", images: [] };
  }
  const content = last.content;
  if (typeof content === "string") {
    return { history: messages.slice(0, -1) as AgentMessage[], text: content, images: [] };
  }
  const text = content
    .filter((part): part is TextContent => part.type === "text")
    .map((part) => part.text)
    .join("\n");
  const images = content.filter((part): part is ImageContent => part.type === "image");
  return { history: messages.slice(0, -1) as AgentMessage[], text, images };
}

function thinkingLevelFor(payload: JsonObject): "off" | "minimal" | "low" | "medium" | "high" | "xhigh" | "max" | undefined {
  const reasoning = asString(payload.reasoning).trim();
  if (!reasoning) return undefined;
  if (["off", "minimal", "low", "medium", "high", "xhigh", "max"].includes(reasoning)) {
    return reasoning as "off" | "minimal" | "low" | "medium" | "high" | "xhigh" | "max";
  }
  return undefined;
}

function emitHarnessEvent(
  state: HarnessSessionState,
  event: Parameters<AgentHarness["subscribe"]>[0] extends (event: infer TEvent, signal?: AbortSignal) => unknown ? TEvent : never,
): void {
  const requestId = state.currentRequestId;
  if (!requestId) return;
  switch (event.type) {
    case "message_update":
      if (event.message.role === "assistant") {
        if (event.assistantMessageEvent.type === "text_delta" || event.assistantMessageEvent.type === "thinking_delta") {
          emitStreamEvent(requestId, event.assistantMessageEvent);
        }
      }
      break;
    case "tool_execution_start":
      state.toolArgsById.set(event.toolCallId, event.args);
      writeEvent(requestId, "tool_call_start", toolEventPayload(event.toolCallId, event.toolName, event.args));
      break;
    case "tool_execution_update":
      writeEvent(requestId, "tool_call_delta", toolEventPayload(event.toolCallId, event.toolName, event.args, event.partialResult));
      break;
    case "tool_execution_end":
      writeEvent(
        requestId,
        "tool_call_end",
        toolEventPayload(
          event.toolCallId,
          event.toolName,
          state.toolArgsById.get(event.toolCallId) ?? {},
          event.result,
          event.isError,
        ),
      );
      state.toolArgsById.delete(event.toolCallId);
      break;
  }
}

function modelConfigSignature(config: ModelConfig): string {
  return JSON.stringify(config);
}

function hostToolSignature(rawTools: unknown): string {
  return JSON.stringify(normalizeHostToolDefinitions(rawTools));
}

function stableJsonValue(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(stableJsonValue);
  if (!value || typeof value !== "object") return value;
  return Object.fromEntries(
    Object.entries(value as JsonObject)
      .sort(([left], [right]) => left.localeCompare(right))
      .map(([key, entry]) => [key, stableJsonValue(entry)]),
  );
}

function historySignature(messages: AgentMessage[]): string {
  return JSON.stringify(
    stableJsonValue(
      messages.map((message) => {
        const normalized = { ...(message as unknown as JsonObject) };
        delete normalized.timestamp;
        if (normalized.role === "user" && typeof normalized.content === "string") {
          normalized.content = [{ type: "text", text: normalized.content }];
        }
        return normalized;
      }),
    ),
  );
}

function latestAssistantMessage(messages: AgentMessage[]): AssistantMessage | undefined {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const message = messages[index];
    if (message.role === "assistant") return message;
  }
  return undefined;
}

async function canReuseHarnessSession(
  state: HarnessSessionState,
  config: ModelConfig,
  toolSignature: string,
  workspaceDirectory: string,
  history: AgentMessage[],
): Promise<boolean> {
  if (state.configSignature !== modelConfigSignature(config)) return false;
  if (state.toolSignature !== toolSignature) return false;
  if (state.workspaceDirectory !== workspaceDirectory) return false;
  const inMemoryContext = await state.session.buildContext();
  return historySignature(history) === historySignature(inMemoryContext.messages);
}

function rejectPendingHostToolsForSession(sessionId: string, message: string): void {
  for (const [toolRequestId, pending] of pendingHostToolRequests) {
    if (pending.sessionId !== sessionId) continue;
    pending.reject(new Error(message));
    pendingHostToolRequests.delete(toolRequestId);
  }
}

async function closeHarnessSession(
  sessionId: string,
  expectedState?: HarnessSessionState,
): Promise<boolean> {
  const state = harnessSessions.get(sessionId);
  if (!state || (expectedState && state !== expectedState)) return false;
  harnessSessions.delete(sessionId);
  rejectPendingHostToolsForSession(sessionId, "Host tool execution ended with the Pi session.");
  await disposeExtensionRuntime(state, "quit");
  state.pendingExtensionRuntime?.runner.invalidate("Pi session closed before reload completed.");
  await state.harness.abort().catch((error) => {
    stderr.write(
      `pi-bridge session close failed: ${error instanceof Error ? error.message : String(error)}\n`,
    );
  });
  return true;
}

async function pruneHarnessSessions(protectedSessionId = ""): Promise<void> {
  const now = Date.now();
  const expired = [...harnessSessions.values()]
    .filter(
      (state) =>
        state.sessionId !== protectedSessionId &&
        !state.currentRequestId &&
        now - state.lastAccessedAt >= harnessSessionTtlMs,
    )
    .sort((left, right) => left.lastAccessedAt - right.lastAccessedAt);
  for (const state of expired) {
    await closeHarnessSession(state.sessionId, state);
  }

  while (harnessSessions.size > harnessSessionLimit) {
    const candidate = [...harnessSessions.values()]
      .filter((state) => state.sessionId !== protectedSessionId && !state.currentRequestId)
      .sort((left, right) => left.lastAccessedAt - right.lastAccessedAt)[0];
    if (!candidate) return;
    await closeHarnessSession(candidate.sessionId, candidate);
  }
}

async function createHarnessSession(
  sessionId: string,
  payload: JsonObject,
  config: ModelConfig,
  history: AgentMessage[],
): Promise<HarnessSessionState> {
  const { models, model, credentialStore } = buildModels(config);
  const workspaceDirectory = asString(payload.workspace_directory, process.cwd()) || process.cwd();
  const sessionRepo = new InMemorySessionRepo();
  const session = await sessionRepo.create({ id: sessionId });
  for (const message of history) {
    await session.appendMessage(message);
  }
  const extensionRuntime = await loadAetherExtensions(
    workspaceDirectory,
    Array.isArray(payload.extension_paths)
      ? payload.extension_paths.filter((value): value is string => typeof value === "string")
      : [],
    extensionLoadOptionsFromPayload(payload),
  );
  const configuredExtensionPaths = Array.isArray(payload.extension_paths)
    ? payload.extension_paths.filter((value): value is string => typeof value === "string")
    : [];
  for (const message of history) {
    extensionRuntime.sessionManager.appendMessage(message as never);
  }
  const state: HarnessSessionState = {
    sessionId,
    configSignature: modelConfigSignature(config),
    toolSignature: hostToolSignature(payload.host_tools),
    workspaceDirectory,
    models,
    model,
    credentialStore,
    session,
    harness: undefined as unknown as AgentHarness,
    hostTools: [],
    extensionRuntime,
    configuredExtensionPaths,
    extensionUnsubscribers: [],
    pendingToolRefresh: false,
    pendingActiveToolNames: undefined,
    currentSignal: undefined,
    systemPrompt: asString(payload.system_prompt),
    currentRequestId: "",
    toolArgsById: new Map<string, unknown>(),
    agentRetryMaxRetries: config.max_retries ?? DEFAULT_AGENT_RETRY_MAX_RETRIES,
    lastAccessedAt: Date.now(),
  };
  state.hostTools = normalizeHostToolDefinitions(payload.host_tools).map((tool) =>
    createHostTool(state, tool),
  );
  const tools = [
    ...state.hostTools,
    ...extensionTools(extensionRuntime),
  ].reduce((toolMap, tool) => toolMap.set(tool.name, tool), new Map<string, AgentTool>());
  const harness = new AgentHarness({
    models,
    session,
    model,
    systemPrompt: () => state.systemPrompt,
    tools: [...tools.values()],
    thinkingLevel: thinkingLevelFor(payload),
    streamOptions: harnessStreamOptions(payload, config),
  });
  state.harness = harness;
  harness.subscribe((event) => emitHarnessEvent(state, event));
  harness.on("tool_result", (event) => {
    const details = asObject(event.details);
    if (asBoolean(details.is_error, false)) return { isError: true };
    return undefined;
  });
  bindExtensionCore(state);
  installExtensionHooks(state);
  harness.subscribe(async (event) => {
    if (event.type === "settled") await applyPendingExtensionChanges(state);
  });
  await extensionRuntime.runner.emit({ type: "session_start", reason: "startup" });
  harnessSessions.set(sessionId, state);
  return state;
}

async function prepareHarnessSession(
  payload: JsonObject,
  history: AgentMessage[],
): Promise<{ state: HarnessSessionState; reused: boolean }> {
  await pruneHarnessSessions();
  const config = normalizeModelConfig(payload.model_config ?? defaultModelConfig);
  const sessionId = asString(payload.session_id).trim();
  if (!sessionId) throw new Error("session_id is required for Pi harness sessions.");
  const workspaceDirectory = asString(payload.workspace_directory, process.cwd()) || process.cwd();
  const toolSignature = hostToolSignature(payload.host_tools);
  const existing = harnessSessions.get(sessionId);
  const reused = existing
    ? await canReuseHarnessSession(existing, config, toolSignature, workspaceDirectory, history)
    : false;
  if (!existing || !reused) {
    if (existing) await closeHarnessSession(sessionId, existing);
    const state = await createHarnessSession(sessionId, payload, config, history);
    await pruneHarnessSessions(sessionId);
    return {
      state,
      reused: false,
    };
  }

  const reusable = existing;
  reusable.lastAccessedAt = Date.now();
  reusable.systemPrompt = asString(payload.system_prompt);
  reusable.agentRetryMaxRetries = config.max_retries ?? DEFAULT_AGENT_RETRY_MAX_RETRIES;
  reusable.configuredExtensionPaths = Array.isArray(payload.extension_paths)
    ? payload.extension_paths.filter((value): value is string => typeof value === "string")
    : [];
  await reusable.harness.setThinkingLevel(thinkingLevelFor(payload) ?? "off");
  await reusable.harness.setStreamOptions(harnessStreamOptions(payload, config));
  reusable.hostTools = normalizeHostToolDefinitions(payload.host_tools).map((tool) =>
    createHostTool(reusable, tool),
  );
  await refreshSessionTools(reusable);
  return { state: reusable, reused: true };
}

// TODO(pi-agent-core): use public continue API when available; pin private helpers for 0.83.0.
const HARNESS_RETRY_METHODS = [
  "createTurnState",
  "createContext",
  "createLoopConfig",
  "createStreamFn",
  "handleAgentEvent",
] as const;

type HarnessRetryMethodName = (typeof HARNESS_RETRY_METHODS)[number];

type HarnessRetryInternals = {
  createTurnState: () => Promise<{ messages: AgentMessage[]; [key: string]: unknown }>;
  createContext: (turnState: unknown) => AgentContext;
  createLoopConfig: (
    getTurnState: () => unknown,
    setTurnState: (turnState: unknown) => void,
  ) => AgentLoopConfig;
  createStreamFn: (getTurnState: () => unknown) => StreamFn;
  handleAgentEvent: (event: AgentHarnessEvent, signal?: AbortSignal) => Promise<void>;
};

function assertAgentCoreRetrySurface(): void {
  if (typeof runAgentLoopContinue !== "function") {
    throw new Error(
      `Pi agent retry requires runAgentLoopContinue from @earendil-works/pi-agent-core@${PI_AGENT_CORE_VERSION}.`,
    );
  }
  const proto = AgentHarness.prototype as unknown as Record<string, unknown>;
  for (const methodName of HARNESS_RETRY_METHODS) {
    if (typeof proto[methodName] !== "function") {
      throw new Error(
        `Pi agent retry requires AgentHarness.${methodName}(); ` +
          `it is not available on @earendil-works/pi-agent-core@${PI_AGENT_CORE_VERSION}.`,
      );
    }
  }
}

function harnessRetryMethod<K extends HarnessRetryMethodName>(
  harness: AgentHarness,
  methodName: K,
): HarnessRetryInternals[K] {
  const proto = AgentHarness.prototype as unknown as Record<string, unknown>;
  const method = proto[methodName];
  if (typeof method !== "function") {
    throw new Error(
      `Pi agent retry requires AgentHarness.${methodName}(); ` +
        `it is not available on @earendil-works/pi-agent-core@${PI_AGENT_CORE_VERSION}.`,
    );
  }
  return (method as (...args: unknown[]) => unknown).bind(harness) as HarnessRetryInternals[K];
}

function harnessRetryInternals(harness: AgentHarness): HarnessRetryInternals {
  assertAgentCoreRetrySurface();
  return {
    createTurnState: harnessRetryMethod(harness, "createTurnState"),
    createContext: harnessRetryMethod(harness, "createContext"),
    createLoopConfig: harnessRetryMethod(harness, "createLoopConfig"),
    createStreamFn: harnessRetryMethod(harness, "createStreamFn"),
    handleAgentEvent: harnessRetryMethod(harness, "handleAgentEvent"),
  };
}

async function continueHarnessTurn(
  state: HarnessSessionState,
  signal: AbortSignal,
): Promise<AssistantMessage> {
  const harness = harnessRetryInternals(state.harness);
  const branch = await state.session.getBranch();
  const failedEntry = [...branch]
    .reverse()
    .find(
      (entry) =>
        entry.type === "message" &&
        entry.message.role === "assistant" &&
        entry.message.stopReason === "error",
    );
  if (!failedEntry || failedEntry.type !== "message") {
    throw new Error("Pi retry requires a failed assistant message in the active session branch.");
  }

  await state.session.moveTo(failedEntry.parentId);
  const extensionBranch = state.extensionRuntime.sessionManager.getBranch();
  const extensionFailure = [...extensionBranch]
    .reverse()
    .find(
      (entry) =>
        entry.type === "message" &&
        entry.message.role === "assistant" &&
        entry.message.stopReason === "error",
    );
  if (extensionFailure?.type === "message") {
    if (extensionFailure.parentId) {
      state.extensionRuntime.sessionManager.branch(extensionFailure.parentId);
    } else {
      state.extensionRuntime.sessionManager.resetLeaf();
    }
  }

  const turnState = await harness.createTurnState();
  let activeTurnState: unknown = turnState;
  const getTurnState = () => activeTurnState;
  const setTurnState = (nextTurnState: unknown) => {
    activeTurnState = nextTurnState;
  };
  const messages = await runAgentLoopContinue(
    harness.createContext(turnState),
    harness.createLoopConfig(getTurnState, setTurnState),
    (event) => harness.handleAgentEvent(event, signal),
    signal,
    harness.createStreamFn(getTurnState),
  );
  const message = [...messages]
    .reverse()
    .find((entry): entry is AssistantMessage => entry.role === "assistant");
  if (!message) throw new Error("Pi retry completed without an assistant message.");
  return message;
}

async function runHarnessPrompt(
  id: string,
  state: HarnessSessionState,
  text: string,
  images: ImageContent[],
): Promise<AssistantMessage> {
  state.lastAccessedAt = Date.now();
  state.currentRequestId = id;
  const retryAbortController = new AbortController();
  activeAborters.set(id, () => {
    retryAbortController.abort();
    return state.harness.abort();
  });
  try {
    let firstAttempt = true;
    return await retryAssistantCall(
      async () => {
        if (firstAttempt) {
          firstAttempt = false;
          const firstMessage = await state.harness.prompt(
            text,
            images.length > 0 ? { images } : undefined,
          );
          await state.harness.waitForIdle();
          return firstMessage;
        }
        return continueHarnessTurn(state, retryAbortController.signal);
      },
      {
        enabled: state.agentRetryMaxRetries > 0,
        maxRetries: state.agentRetryMaxRetries,
        baseDelayMs: DEFAULT_AGENT_RETRY_BASE_DELAY_MS,
      },
      retryAbortController.signal,
    );
  } finally {
    activeAborters.delete(id);
    if (state.currentRequestId === id) state.currentRequestId = "";
    state.lastAccessedAt = Date.now();
  }
}

async function runHarnessTurn(id: string, payload: JsonObject): Promise<JsonObject> {
  const messages = normalizeMessages(payload.messages);
  const prompt = promptFromLastUserMessage(messages);
  const { state, reused } = await prepareHarnessSession(payload, prompt.history);
  const message = await runHarnessPrompt(id, state, prompt.text, prompt.images);
  return {
    ...assistantPayload(message),
    ...(await credentialPayload(state.credentialStore)),
    session_id: state.sessionId,
    session_reused: reused,
  };
}

function bridgePrompt(rawMessage: unknown): { text: string; images: ImageContent[] } {
  const messages = normalizeMessages([rawMessage]);
  const message = messages[0];
  if (!message || message.role !== "user") {
    throw new Error("A user message is required.");
  }
  if (typeof message.content === "string") {
    return { text: message.content, images: [] };
  }
  return {
    text: message.content
      .filter((part): part is TextContent => part.type === "text")
      .map((part) => part.text)
      .join("\n"),
    images: message.content.filter((part): part is ImageContent => part.type === "image"),
  };
}

async function lastSessionAssistant(state: HarnessSessionState): Promise<AssistantMessage> {
  const context = await state.session.buildContext();
  const message = latestAssistantMessage(context.messages);
  if (!message) throw new Error(`Pi session ${state.sessionId} has no assistant response.`);
  return message;
}

async function steerHarnessSession(payload: JsonObject): Promise<JsonObject> {
  await pruneHarnessSessions();
  const sessionId = asString(payload.session_id).trim();
  const state = harnessSessions.get(sessionId);
  if (!state || !state.currentRequestId) return { accepted: false };
  state.lastAccessedAt = Date.now();
  const prompt = bridgePrompt(payload.message);
  try {
    await state.harness.steer(prompt.text, prompt.images.length > 0 ? { images: prompt.images } : undefined);
    return { accepted: true };
  } catch {
    return { accepted: false };
  }
}

async function followUpHarnessSession(id: string, payload: JsonObject): Promise<JsonObject> {
  await pruneHarnessSessions();
  const sessionId = asString(payload.session_id).trim();
  const state = harnessSessions.get(sessionId);
  if (!state) throw new Error(`Unknown Pi session: ${sessionId}`);
  state.lastAccessedAt = Date.now();
  const prompt = bridgePrompt(payload.message);
  if (state.currentRequestId) {
    await state.harness.followUp(prompt.text, prompt.images.length > 0 ? { images: prompt.images } : undefined);
    await state.harness.waitForIdle();
    return {
      ...assistantPayload(await lastSessionAssistant(state)),
      ...(await credentialPayload(state.credentialStore)),
    };
  }
  return {
    ...assistantPayload(await runHarnessPrompt(id, state, prompt.text, prompt.images)),
    ...(await credentialPayload(state.credentialStore)),
  };
}

async function runSimpleCompletion(id: string, payload: JsonObject, stream: boolean): Promise<JsonObject> {
  const config = normalizeModelConfig(payload.model_config ?? defaultModelConfig);
  const { models, model, credentialStore } = buildModels(config);
  const controller = new AbortController();
  activeAborters.set(id, () => controller.abort());
  try {
    const context = buildContext(payload);
    const options = streamOptionsFor(payload, controller.signal, config, model);
    if (stream) {
      const eventStream = models.streamSimple(model, context, options);
      for await (const event of eventStream) {
        emitStreamEvent(id, event);
      }
      const message = await eventStream.result();
      return {
        ...assistantPayload(message),
        ...(await credentialPayload(credentialStore)),
      };
    }
    const message = await models.completeSimple(model, context, options);
    return {
      ...assistantPayload(message),
      ...(await credentialPayload(credentialStore)),
    };
  } finally {
    activeAborters.delete(id);
  }
}

function requestAuthPrompt(
  requestId: string,
  prompt: {
    type: "text" | "secret" | "select" | "manual_code";
    message: string;
    placeholder?: string;
    options?: readonly { id: string; label: string; description?: string }[];
    signal?: AbortSignal;
  },
): Promise<string> {
  const promptId = `auth-prompt-${Date.now()}-${++authPromptCounter}`;
  writeEvent(requestId, "auth_prompt", {
    prompt_id: promptId,
    prompt_type: prompt.type,
    message: prompt.message,
    placeholder: prompt.placeholder ?? "",
    options: prompt.options ?? [],
  });
  return new Promise<string>((resolve, reject) => {
    if (prompt.signal?.aborted) {
      reject(new Error("Authentication prompt was cancelled."));
      return;
    }
    const abortListener = () => {
      pendingAuthPrompts.delete(promptId);
      reject(new Error("Authentication prompt was cancelled."));
    };
    prompt.signal?.addEventListener("abort", abortListener, { once: true });
    pendingAuthPrompts.set(promptId, {
      requestId,
      resolve: (value) => {
        prompt.signal?.removeEventListener("abort", abortListener);
        resolve(value);
      },
      reject: (error) => {
        prompt.signal?.removeEventListener("abort", abortListener);
        reject(error);
      },
    });
  });
}

function resolveAuthPrompt(payload: JsonObject): boolean {
  const promptId = asString(payload.prompt_id).trim();
  const pending = promptId ? pendingAuthPrompts.get(promptId) : undefined;
  if (!pending) return false;
  pendingAuthPrompts.delete(promptId);
  if (asBoolean(payload.cancelled, false)) {
    pending.reject(new Error("Authentication was cancelled."));
  } else {
    pending.resolve(asString(payload.value));
  }
  return true;
}

async function loginProvider(id: string, payload: JsonObject): Promise<JsonObject> {
  const providerConfigId = asString(payload.provider_config_id).trim();
  if (!providerConfigId) throw new Error("provider_config_id is required for provider login.");
  const providerId = asString(payload.provider_id).trim();
  const provider = builtinProviderById.get(providerId);
  if (!provider) throw new Error(`Unknown built-in Pi provider: ${providerId}`);
  const authMethod = asString(payload.auth_method, "oauth").trim();
  const oauthFlow = asString(payload.oauth_flow).trim();
  const controller = new AbortController();
  activeAborters.set(id, () => controller.abort());
  const callbacks: AuthInteraction = {
    signal: controller.signal,
    prompt: (prompt) => {
      if (providerId === "openai-codex" && oauthFlow && prompt.type === "select") {
        const selected = prompt.options?.find((option) => option.id === oauthFlow);
        if (selected) return Promise.resolve(selected.id);
      }
      return requestAuthPrompt(id, prompt);
    },
    notify: (event) => {
      switch (event.type) {
        case "auth_url":
          writeEvent(id, "auth_url", {
            url: event.url,
            instructions: event.instructions ?? "",
          });
          break;
        case "device_code":
          writeEvent(id, "auth_device_code", {
            user_code: event.userCode,
            verification_uri: event.verificationUri,
            interval_seconds: event.intervalSeconds,
            expires_in_seconds: event.expiresInSeconds,
          });
          break;
        case "progress":
          writeEvent(id, "auth_progress", { message: event.message });
          break;
        case "info":
          writeEvent(id, "auth_progress", {
            message: event.message,
            links: event.links ?? [],
          });
          break;
      }
    },
  };
  try {
    if (authMethod === "api_key") {
      const apiKey = provider.auth.apiKey;
      if (!apiKey) throw new Error(`Pi provider ${providerId} does not support API key authentication.`);
      if (!apiKey.login) {
        throw new Error(`Pi provider ${providerId} uses ambient credentials and has no interactive login.`);
      }
      const credential = await apiKey.login(callbacks);
      return {
        provider_id: providerId,
        auth_method: "api_key",
        api_key: credential.key ?? "",
        provider_env: credential.env ?? {},
      };
    }
    if (authMethod !== "oauth") {
      throw new Error(`Unsupported Pi authentication method: ${authMethod}`);
    }
    const oauth = provider.auth.oauth;
    if (!oauth) throw new Error(`Pi provider ${providerId} does not support OAuth.`);
    const credential = await oauth.login(callbacks);
    await replaceSharedCredential(providerConfigId, credential);
    return {
      provider_id: providerId,
      auth_method: "oauth",
      oauth_credential: credential as unknown as JsonObject,
    };
  } finally {
    activeAborters.delete(id);
    for (const [promptId, prompt] of pendingAuthPrompts) {
      if (prompt.requestId === id) {
        prompt.reject(new Error("Authentication flow ended before the prompt completed."));
        pendingAuthPrompts.delete(promptId);
      }
    }
  }
}

async function clearProviderCredential(payload: JsonObject): Promise<JsonObject> {
  const providerConfigId = asString(payload.provider_config_id).trim();
  if (!providerConfigId) throw new Error("provider_config_id is required to clear provider credentials.");
  return { cleared: await clearSharedCredential(providerConfigId) };
}

async function closeHarnessSessionRequest(payload: JsonObject): Promise<JsonObject> {
  const sessionId = asString(payload.session_id).trim();
  if (!sessionId) throw new Error("session_id is required to close a Pi harness session.");
  return { closed: await closeHarnessSession(sessionId) };
}

function extensionRuntimePayload(state: HarnessSessionState): JsonObject {
  const runner = state.extensionRuntime.runner;
  return {
    session_id: state.sessionId,
    workspace_directory: state.workspaceDirectory,
    extension_paths: runner.getExtensionPaths(),
    discovered_paths: state.extensionRuntime.paths,
    errors: state.extensionRuntime.errors,
    tools: runner.getAllRegisteredTools().map((tool) => ({
      name: tool.definition.name,
      description: tool.definition.description,
      source_path: tool.sourceInfo.path,
    })),
    commands: runner.getRegisteredCommands().map((command) => ({
      name: command.invocationName,
      description: command.description ?? "",
      source_path: command.sourceInfo.path,
    })),
    pending_reload: Boolean(state.pendingExtensionRuntime),
    ui_mode: "print",
    custom_tui_supported: false,
  };
}

function extensionSessionFromPayload(payload: JsonObject): HarnessSessionState {
  const sessionId = asString(payload.session_id).trim();
  if (!sessionId) throw new Error("session_id is required for Pi extension operations.");
  const state = harnessSessions.get(sessionId);
  if (!state) throw new Error(`Unknown Pi session: ${sessionId}`);
  return state;
}

async function listExtensions(payload: JsonObject): Promise<JsonObject> {
  await pruneHarnessSessions();
  return extensionRuntimePayload(extensionSessionFromPayload(payload));
}

async function reloadExtensions(payload: JsonObject): Promise<JsonObject> {
  await pruneHarnessSessions();
  const state = extensionSessionFromPayload(payload);
  const configuredPaths = Array.isArray(payload.extension_paths)
    ? payload.extension_paths.filter((value): value is string => typeof value === "string")
    : [];
  const result = await reloadExtensionsForState(
    state,
    configuredPaths,
    extensionLoadOptionsFromPayload(payload),
  );
  return {
    ...extensionRuntimePayload(state),
    ...result,
  };
}

async function invokeExtensionCommand(payload: JsonObject): Promise<JsonObject> {
  await pruneHarnessSessions();
  const state = extensionSessionFromPayload(payload);
  const commandName = asString(payload.command).trim().replace(/^\//, "");
  if (!commandName) throw new Error("command is required for Pi extension command invocation.");
  const command = state.extensionRuntime.runner.getCommand(commandName);
  if (!command) throw new Error(`Unknown Pi extension command: ${commandName}`);
  const context = state.extensionRuntime.runner.createCommandContext() as ExtensionCommandContext;
  await command.handler(asString(payload.args), context);
  return {
    invoked: true,
    command: commandName,
    pending_reload: Boolean(state.pendingExtensionRuntime),
  };
}

async function installedExtensionPackagesPayload(): Promise<JsonObject> {
  return {
    packages: (await listAetherExtensionPackages(process.cwd())).map((installedPackage) => ({
      source: installedPackage.source,
      scope: installedPackage.scope,
      filtered: installedPackage.filtered,
      installed_path: installedPackage.installedPath ?? "",
      name: installedPackage.name,
      version: installedPackage.version,
      description: installedPackage.description,
      extension_count: installedPackage.extensionCount,
      aether_extension_count: installedPackage.aetherExtensionCount,
      native_entrypoint_count: installedPackage.nativeEntrypointCount,
      skill_count: installedPackage.skillCount,
      prompt_count: installedPackage.promptCount,
      theme_count: installedPackage.themeCount,
      skill_paths: installedPackage.skillPaths,
    })),
  };
}

async function reloadAllExtensionSessions(
  payload: JsonObject = {},
): Promise<JsonObject> {
  const loadOptions = extensionLoadOptionsFromPayload(payload);
  const results: JsonObject[] = [];
  for (const state of harnessSessions.values()) {
    const result = await reloadExtensionsForState(
      state,
      state.configuredExtensionPaths,
      loadOptions,
    );
    results.push({
      session_id: state.sessionId,
      ...result,
    });
  }
  const aetherReload = await loadAetherAppExtensions(process.cwd(), loadOptions);
  const sessionReloadSucceeded = results.every((result) =>
    Array.isArray(result.errors) && result.errors.length === 0
  );
  return {
    succeeded: sessionReloadSucceeded && aetherReload.reloaded,
    session_count: results.length,
    sessions: results,
    aether_reload: aetherReload,
    aether: await aetherAppExtensionSnapshot(),
  };
}

async function installExtensionPackage(payload: JsonObject): Promise<JsonObject> {
  const source = asString(payload.source).trim();
  await installAetherExtensionPackage(process.cwd(), source);
  return {
    installed: true,
    source,
    ...(await installedExtensionPackagesPayload()),
    reload: await reloadAllExtensionSessions(payload),
  };
}

async function removeExtensionPackage(payload: JsonObject): Promise<JsonObject> {
  const source = asString(payload.source).trim();
  const removed = await removeAetherExtensionPackage(process.cwd(), source);
  return {
    removed,
    source,
    ...(await installedExtensionPackagesPayload()),
    reload: removed
      ? await reloadAllExtensionSessions(payload)
      : { session_count: 0, sessions: [] },
  };
}

async function updateExtensionPackage(payload: JsonObject): Promise<JsonObject> {
  const source = asString(payload.source).trim();
  await updateAetherExtensionPackage(process.cwd(), source);
  return {
    updated: true,
    source,
    ...(await installedExtensionPackagesPayload()),
    reload: await reloadAllExtensionSessions(payload),
  };
}

async function reloadAetherAppExtensionsRequest(
  id: string,
  payload: JsonObject,
): Promise<JsonObject> {
  return runAetherOperation(id, async () => {
    const result = await loadAetherAppExtensions(
      process.cwd(),
      extensionLoadOptionsFromPayload(payload),
    );
    return {
      ...result,
      snapshot: await aetherAppExtensionSnapshot(asObject(payload.context)),
    };
  });
}

async function getAetherAppExtensionsRequest(
  id: string,
  payload: JsonObject,
): Promise<JsonObject> {
  return runAetherOperation(id, async () => ({
    snapshot: await aetherAppExtensionSnapshot(asObject(payload.context)),
  }));
}

async function invokeAetherAppExtensionActionRequest(
  id: string,
  payload: JsonObject,
): Promise<JsonObject> {
  return runAetherOperation(id, async () => ({
    ...(await invokeAetherAppExtensionAction(
      asString(payload.extension_id),
      asString(payload.action),
      asObject(payload.args),
      asObject(payload.context),
    )),
    snapshot: await aetherAppExtensionSnapshot(asObject(payload.context)),
  }));
}

async function dispatchAetherAppExtensionEventRequest(
  id: string,
  payload: JsonObject,
): Promise<JsonObject> {
  return runAetherOperation(id, async () => ({
    ...(await dispatchAetherAppExtensionEvent(
      asString(payload.event),
      asObject(payload.data),
      asObject(payload.context),
    )),
    snapshot: await aetherAppExtensionSnapshot(asObject(payload.context)),
  }));
}

async function abortBridgeTarget(payload: JsonObject): Promise<JsonObject> {
  const targetId = asString(payload.request_id, asString(payload.target_id)).trim();
  const sessionId = asString(payload.session_id).trim();
  const aborter = targetId ? activeAborters.get(targetId) : undefined;
  const state = sessionId ? harnessSessions.get(sessionId) : undefined;
  if (aborter) {
    void Promise.resolve(aborter()).catch((error) => {
      stderr.write(`pi-bridge abort failed: ${error instanceof Error ? error.message : String(error)}\n`);
    });
  } else if (state) {
    void state.harness.abort().catch((error) => {
      stderr.write(`pi-bridge abort failed: ${error instanceof Error ? error.message : String(error)}\n`);
    });
  }
  for (const [toolRequestId, pending] of pendingHostToolRequests) {
    if (sessionId && pending.sessionId === sessionId) {
      pending.reject(new Error("Host tool execution aborted with the Pi session."));
      pendingHostToolRequests.delete(toolRequestId);
    }
  }
  for (const [promptId, pending] of pendingAuthPrompts) {
    if (targetId && pending.requestId === targetId) {
      pending.reject(new Error("Authentication was cancelled."));
      pendingAuthPrompts.delete(promptId);
    }
  }
  return { aborted: Boolean(aborter || state) };
}

async function handleRequest(request: BridgeRequest): Promise<void> {
  const id = asString(request.id);
  const type = asString(request.type);
  const payload = asObject(request.payload);
  if (!id) throw new Error("Request id is required.");
  switch (type) {
    case "ping":
      writeResponse(id, {
        bridge_version: BRIDGE_VERSION,
        pi_ai_version: PI_AI_VERSION,
        pi_agent_core_version: PI_AGENT_CORE_VERSION,
        pi_coding_agent_version: PI_CODING_AGENT_VERSION,
        node_version: process.version,
      });
      return;
    case "list_providers":
      writeResponse(id, providerCatalogPayload());
      return;
    case "login_provider":
      writeResponse(id, await loginProvider(id, payload));
      return;
    case "clear_provider_credential":
      writeResponse(id, await clearProviderCredential(payload));
      return;
    case "auth_prompt_result":
      writeResponse(id, { accepted: resolveAuthPrompt(payload) });
      return;
    case "set_model_config":
      defaultModelConfig = normalizeModelConfig(payload.model_config);
      writeResponse(id, { configured: true });
      return;
    case "complete_once":
      writeResponse(id, await runSimpleCompletion(id, payload, asBoolean(payload.stream, false)));
      return;
    case "run_turn":
      writeResponse(id, await runHarnessTurn(id, payload));
      return;
    case "close_session":
      writeResponse(id, await closeHarnessSessionRequest(payload));
      return;
    case "list_extensions":
      writeResponse(id, await listExtensions(payload));
      return;
    case "reload_extensions":
      writeResponse(id, await reloadExtensions(payload));
      return;
    case "invoke_extension_command":
      writeResponse(id, await invokeExtensionCommand(payload));
      return;
    case "list_extension_packages":
      writeResponse(id, await installedExtensionPackagesPayload());
      return;
    case "install_extension_package":
      writeResponse(id, await installExtensionPackage(payload));
      return;
    case "remove_extension_package":
      writeResponse(id, await removeExtensionPackage(payload));
      return;
    case "update_extension_package":
      writeResponse(id, await updateExtensionPackage(payload));
      return;
    case "reload_all_extensions":
      writeResponse(id, await reloadAllExtensionSessions(payload));
      return;
    case "reload_aether_extensions":
      writeResponse(id, await reloadAetherAppExtensionsRequest(id, payload));
      return;
    case "get_aether_extensions":
      writeResponse(id, await getAetherAppExtensionsRequest(id, payload));
      return;
    case "invoke_aether_extension_action":
      writeResponse(id, await invokeAetherAppExtensionActionRequest(id, payload));
      return;
    case "dispatch_aether_extension_event":
      writeResponse(id, await dispatchAetherAppExtensionEventRequest(id, payload));
      return;
    case "subscribe_aether_extensions":
      aetherSubscriberRequestIds.add(id);
      writeEvent(id, "aether_invalidated", { subscribed: true });
      return;
    case "unsubscribe_aether_extensions":
      aetherSubscriberRequestIds.delete(asString(payload.request_id, id));
      writeResponse(id, { unsubscribed: true });
      return;
    case "aether_host_result":
      writeResponse(id, { accepted: resolveAetherHostCall(payload) });
      return;
    case "steer":
      writeResponse(id, await steerHarnessSession(payload));
      return;
    case "follow_up":
      writeResponse(id, await followUpHarnessSession(id, payload));
      return;
    case "host_tool_result":
      writeResponse(id, { accepted: resolveHostToolResult(payload) });
      return;
    case "host_tool_progress":
      writeResponse(id, { accepted: applyHostToolProgress(payload) });
      return;
    case "abort":
      writeResponse(id, await abortBridgeTarget(payload));
      return;
    default:
      throw new Error(`Unsupported request type: ${type}`);
  }
}

async function main(): Promise<void> {
  if (process.argv.includes("--ping")) {
    writeFrame({
      type: "response",
      id: "ping",
      ok: true,
      payload: {
        bridge_version: BRIDGE_VERSION,
        pi_ai_version: PI_AI_VERSION,
        pi_agent_core_version: PI_AGENT_CORE_VERSION,
        pi_coding_agent_version: PI_CODING_AGENT_VERSION,
        node_version: process.version,
      },
    });
    return;
  }

  const reader = createInterface({ input, crlfDelay: Infinity });
  for await (const line of reader) {
    if (!line.trim()) continue;
    let request: BridgeRequest;
    try {
      request = JSON.parse(line) as BridgeRequest;
    } catch (error) {
      writeError(undefined, error, "invalid_json");
      continue;
    }
    handleRequest(request).catch((error) => {
      writeError(request.id, error);
    });
  }
}

main().catch((error) => {
  stderr.write(`pi-bridge fatal: ${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  process.exitCode = 1;
});