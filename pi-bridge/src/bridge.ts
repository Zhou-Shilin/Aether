import { AsyncLocalStorage } from "node:async_hooks";
import { flushCompileCache } from "node:module";
import * as fs from "node:fs/promises";
import * as os from "node:os";
import * as path from "node:path";
import { createInterface } from "node:readline";
import { stdin as input, stdout as output, stderr } from "node:process";
import {
  getSupportedThinkingLevels,
  clampThinkingLevel,
  createModels,
  createProvider,
  hasApi,
  defaultProviderAuthContext,
  fauxAssistantMessage,
  fauxProvider,
  fauxToolCall,
  InMemoryModelsStore,
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
  type Provider,
  type OAuthAuth,
  type MutableModels,
  type ProviderStreams,
  type SimpleStreamOptions,
  type TextContent,
  type Usage,
  Type,
} from "@earendil-works/pi-ai";
import {
  builtinProviders,
  getBuiltinModels,
  getBuiltinProviders,
} from "@earendil-works/pi-ai/providers/all";
import { registerBunOAuthFlows } from "@earendil-works/pi-ai/bun-oauth";
import {
  type AgentMessage,
  type AgentToolResult,
} from "@earendil-works/pi-agent-core/node";
import {
  AgentSession,
  DefaultResourceLoader,
  ModelRuntime,
  SessionManager,
  SettingsManager,
  createAgentSession,
  createBashToolDefinition,
  createLocalBashOperations,
  createEditToolDefinition,
  createFindToolDefinition,
  createGrepToolDefinition,
  createLsToolDefinition,
  createReadToolDefinition,
  createWriteToolDefinition,
  type AgentSessionEvent,
  type BashOperations,
  type EditOperations,
  type ExtensionCommandContext,
  type ExtensionUIContext,
  type ReadOperations,
  type ToolDefinition,
  type WriteOperations,
  type ExtensionFactory,
} from "@earendil-works/pi-coding-agent";
import { openAICompletionsApi } from "@earendil-works/pi-ai/api/openai-completions.lazy";
import type { TSchema } from "typebox";
import {
  discoverAetherExtensionPaths,
  discoverPackageExtensionPaths,
  installAetherExtensionPackage,
  listAetherExtensionPackages,
  removeAetherExtensionPackage,
  updateAetherExtensionPackage,
} from "./extensions.js";
import {
  aetherAppExtensionSnapshot,
  configureAetherExtensionTransport,
  dispatchAetherAppExtensionEvent,
  invokeAetherAppExtensionAction,
  loadAetherAppExtensions,
} from "./aether-extensions.js";
import { bridgeDebug, bridgeDebugEnabled, elapsedMillis } from "./debug.js";

registerBunOAuthFlows();

const BRIDGE_VERSION = "2.0.0-alpha.0";
const PI_AI_VERSION = "0.84.1";
const PI_AGENT_CORE_VERSION = "0.84.1";
const PI_CODING_AGENT_VERSION = "0.84.1";
const AETHER_LOOPBACK_OAUTH_CALLBACK_HOST = "127.0.0.1";
const OAUTH_FETCH_MAX_ATTEMPTS = 3;
const DEFAULT_AGENT_RETRY_MAX_RETRIES = 5;
const RUNTIME_OPERATION_CHUNK_BYTES = 64 * 1024;

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
  thinking_level_map?: Record<string, string | null>;
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

interface PendingRuntimeOperation {
  sessionId: string;
  resolve: (result: JsonObject) => void;
  reject: (error: Error) => void;
  onChunk?: (chunk: Buffer) => void;
}

interface PendingAetherHostCall {
  resolve: (result: JsonObject) => void;
  reject: (error: Error) => void;
  timeout: NodeJS.Timeout;
}

interface AgentSessionState {
  sessionId: string;
  configSignature: string;
  toolSignature: string;
  extensionSignature: string;
  skillSignature: string;
  workspaceDirectory: string;
  termuxWorkspaceDirectory: string;
  runtime: "alpine" | "termux";
  platform: "android" | "ios";
  chromeEnabled: boolean;
  modelRuntime: ModelRuntime;
  model: Model<string>;
  credentialStore?: BridgeCredentialStore;
  session: AgentSession;
  resourceLoader: DefaultResourceLoader;
  settingsManager: SettingsManager;
  configuredExtensionPaths: string[];
  pendingReload: boolean;
  pendingRecreate: boolean;
  currentRequestId: string;
  turnStartedAtMillis?: number;
  firstAssistantEventAtMillis?: number;
  toolArgsById: Map<string, unknown>;
  lastAccessedAt: number;
}

const activeAborters = new Map<string, () => void | Promise<unknown>>();
const pendingHostToolRequests = new Map<string, PendingHostToolRequest>();
const pendingRuntimeOperations = new Map<string, PendingRuntimeOperation>();
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
const agentSessions = new Map<string, AgentSessionState>();
let currentExtensionLoadOptions = {
  disabledExtensionPaths: [] as string[],
  disabledPackageSources: [] as string[],
};

interface NativeExtensionLoadOptions {
  disabledExtensionPaths: string[];
  disabledPackageSources: string[];
}

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
let runtimeOperationCounter = 0;
let authPromptCounter = 0;
let aetherHostCallCounter = 0;
let compileCacheFlushed = false;

function flushStartupCompileCache(): void {
  if (compileCacheFlushed) return;
  compileCacheFlushed = true;
  try {
    flushCompileCache();
  } catch {
    // Compile caching is an optimization and must not affect bridge requests.
  }
}

function aetherOAuthAuth(providerId: string, oauth: OAuthAuth | undefined): OAuthAuth | undefined {
  if (!oauth) return undefined;
  if (providerId !== "openai-codex") return oauth;
  return {
    ...oauth,
    login: async (interaction) =>
      withAetherOAuthTransport(providerId, interaction, () =>
        oauth.login({
          ...interaction,
          prompt: (prompt) =>
            interaction.prompt(
              prompt.type === "manual_code"
                ? { ...prompt, placeholder: "http://localhost:..." }
                : prompt,
            ),
          notify: (event) => {
            if (event.type === "auth_url") {
              interaction.notify({
                ...event,
                instructions: "Complete login in the authentication window.",
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
    const startedAt = Date.now();
    bridgeDebug("credential_read_start", { provider_id: providerId });
    await this.state.queue;
    bridgeDebug("credential_read_done", {
      provider_id: providerId,
      has_credential: this.state.credential !== undefined,
      elapsed_ms: elapsedMillis(startedAt),
    });
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
    const startedAt = Date.now();
    bridgeDebug("credential_modify_start", { provider_id: providerId });
    let result: Credential | undefined;
    const operation = this.state.queue.then(async () => {
      const next = await fn(this.state.credential);
      if (next !== undefined) this.state.credential = next;
      result = this.state.credential;
    });
    this.state.queue = operation.catch(() => undefined);
    try {
      await operation;
      bridgeDebug("credential_modify_done", {
        provider_id: providerId,
        elapsed_ms: elapsedMillis(startedAt),
      });
    } catch (error) {
      bridgeDebug("credential_modify_failed", {
        provider_id: providerId,
        error: errorMessageWithCause(error),
        elapsed_ms: elapsedMillis(startedAt),
      });
      throw error;
    }
    return result;
  }

  async delete(providerId: string): Promise<void> {
    if (providerId !== this.providerId) return;
    bridgeDebug("credential_delete_start", { provider_id: providerId });
    const operation = this.state.queue.then(() => {
      this.state.credential = undefined;
    });
    this.state.queue = operation.catch(() => undefined);
    await operation;
    bridgeDebug("credential_delete_done", { provider_id: providerId });
  }
}

async function replaceSharedCredential(
  providerConfigId: string,
  credential: Credential,
): Promise<void> {
  bridgeDebug("shared_credential_replace", { provider_config_id: providerConfigId });
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
  bridgeDebug("shared_credential_clear", { provider_config_id: providerConfigId });
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
    bridgeDebug("aether_host_call_no_subscriber", { method });
    throw new Error("The Aether app host is not subscribed.");
  }
  const callId = `aether-host-${Date.now()}-${++aetherHostCallCounter}`;
  bridgeDebug("aether_host_call_start", { call_id: callId, method, routed_request_id: requestId });
  writeEvent(requestId, "aether_host_call", {
    call_id: callId,
    method,
    args,
  });
  const startedAt = Date.now();
  return new Promise<JsonObject>((resolve, reject) => {
    const timeout = setTimeout(() => {
      pendingAetherHostCalls.delete(callId);
      bridgeDebug("aether_host_call_timeout", {
        call_id: callId,
        method,
        elapsed_ms: elapsedMillis(startedAt),
      });
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
  if (!pending) {
    bridgeDebug("aether_host_result_orphaned", { call_id: callId });
    return false;
  }
  pendingAetherHostCalls.delete(callId);
  clearTimeout(pending.timeout);
  bridgeDebug("aether_host_call_resolved", { call_id: callId, ok: asBoolean(payload.ok, true) });
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
  const messages: string[] = [];
  const seen = new Set<object>();
  const pending: unknown[] = [error];
  while (pending.length > 0 && seen.size < 32) {
    const current = pending.shift();
    if (!current || typeof current !== "object" || seen.has(current)) continue;
    seen.add(current);
    const record = current as {
      message?: unknown;
      code?: unknown;
      errno?: unknown;
      syscall?: unknown;
      address?: unknown;
      hostname?: unknown;
      port?: unknown;
      cause?: unknown;
      reason?: unknown;
      errors?: unknown;
    };
    const message = typeof record.message === "string" ? record.message.trim() : "";
    const code = typeof record.code === "string" || typeof record.code === "number"
      ? String(record.code).trim()
      : "";
    const errno = typeof record.errno === "string" || typeof record.errno === "number"
      ? String(record.errno).trim()
      : "";
    const syscall = typeof record.syscall === "string" ? record.syscall.trim() : "";
    const host = [record.hostname ?? record.address, record.port]
      .filter((value) => typeof value === "string" || typeof value === "number")
      .map(String)
      .filter(Boolean)
      .join(":");
    const context = [code, errno !== code ? errno : "", syscall, host]
      .filter((value) => value && !message.includes(value))
      .join(", ");
    const detail = context
      ? `${message || "Network request failed"} (${context})`
      : message;
    if (detail && !messages.includes(detail)) messages.push(detail);
    pending.push(record.cause, record.reason);
    if (Array.isArray(record.errors)) pending.push(...record.errors);
  }
  if (messages.length > 0) return messages.join(": ");
  return error instanceof Error ? error.name : String(error);
}

function fetchWithDetailedErrors(
  fetchImplementation: typeof fetch,
  onError?: (detail: string) => void,
): typeof fetch {
  return async (input, init) => {
    const url = fetchUrl(input);
    const method = typeof init?.method === "string" ? init.method : "GET";
    const startedAt = Date.now();
    bridgeDebug("fetch_start", { method, url });
    try {
      const response = await fetchImplementation(input, init);
      bridgeDebug("fetch_end", {
        method,
        url,
        status: response.status,
        elapsed_ms: elapsedMillis(startedAt),
      });
      return response;
    } catch (error) {
      const detail = errorMessageWithCause(error);
      bridgeDebug("fetch_error", {
        method,
        url,
        error: detail,
        aborted: Boolean(init?.signal?.aborted),
        elapsed_ms: elapsedMillis(startedAt),
      });
      onError?.(detail);
      if (error instanceof Error && detail === error.message) throw error;
      throw new Error(detail, { cause: error });
    }
  };
}

globalThis.fetch = fetchWithDetailedErrors(globalThis.fetch.bind(globalThis));

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
  process.env.PI_OAUTH_CALLBACK_HOST = AETHER_LOOPBACK_OAUTH_CALLBACK_HOST;
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

function normalizeBaseUrlForComparison(value: string | undefined): string {
  const trimmed = (value ?? "").trim();
  if (!trimmed) return "";
  try {
    const url = new URL(trimmed);
    url.pathname = url.pathname.replace(/\/+$/, "");
    return url.toString();
  } catch {
    return trimmed.replace(/\/+$/, "");
  }
}

function isCustomResponsesEndpoint(
  model: Model<string>,
  configuredBaseUrl: string | undefined,
  defaultBaseUrl: string | undefined,
): model is Model<"openai-responses"> {
  const configured = normalizeBaseUrlForComparison(configuredBaseUrl);
  return (
    hasApi(model, "openai-responses") &&
    configured.length > 0 &&
    configured !== normalizeBaseUrlForComparison(defaultBaseUrl)
  );
}

const ISOLATED_REASONING_REPLAY_MODEL_SUFFIX = "#aether-isolated-reasoning-replay";

function isolateResponsesReasoningReplay<T extends { role: string }>(messages: T[]): T[] {
  return messages.map((message) => {
    if (
      message.role !== "assistant" ||
      !("api" in message) ||
      message.api !== "openai-responses" ||
      !("model" in message) ||
      typeof message.model !== "string" ||
      message.model.endsWith(ISOLATED_REASONING_REPLAY_MODEL_SUFFIX)
    ) {
      return message;
    }
    return {
      ...message,
      model: `${message.model}${ISOLATED_REASONING_REPLAY_MODEL_SUFFIX}`,
    };
  });
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

function normalizeThinkingLevelMap(rawValue: unknown): Record<string, string | null> | undefined {
  const raw = asObject(rawValue);
  if (!raw || Object.keys(raw).length === 0) return undefined;
  const result: Record<string, string | null> = {};
  for (const [key, value] of Object.entries(raw)) {
    if (value === null) {
      result[key] = null;
    } else if (typeof value === "string") {
      result[key] = value;
    }
  }
  return Object.keys(result).length > 0 ? result : undefined;
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
  bridgeDebug("model_config_normalized", {
    provider_type: providerType,
    pi_provider_id: piProviderId,
    pi_api: piApi,
    model_id: modelId,
    base_url: baseUrl,
    auth_method: authMethod || "api_key",
    has_api_key: asString(raw.api_key) !== "",
    custom_header_names: Object.keys(normalizeHeaders(raw.custom_headers)),
    timeout_ms: asNumber(raw.timeout_ms, 360000),
    max_retries: Math.max(0, asNumber(raw.max_retries, DEFAULT_AGENT_RETRY_MAX_RETRIES)),
    max_retry_delay_ms: asNumber(raw.max_retry_delay_ms, 60000),
    reasoning: asBoolean(raw.reasoning, false),
  });
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
    thinking_level_map: normalizeThinkingLevelMap(raw.thinking_level_map),
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
    thinkingLevelMap: config.thinking_level_map,
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

function buildModels(config: ModelConfig): {
  models: MutableModels;
  model: Model<string>;
  provider: Provider;
  isolateReasoningReplay: boolean;
  credentialStore?: BridgeCredentialStore;
} {
  bridgeDebug("build_models_start", {
    provider_type: config.provider_type,
    pi_provider_id: config.pi_provider_id,
    pi_api: config.pi_api,
    model_id: config.model_id,
    base_url: config.base_url,
    auth_method: config.auth_method,
  });
  if (config.provider_type === "faux") {
    bridgeDebug("build_models_path", { path: "faux" });
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
    return { models, model, provider: faux.provider, isolateReasoningReplay: false };
  }

  if (config.provider_type === "builtin") {
    bridgeDebug("build_models_path", { path: "builtin", pi_provider_id: config.pi_provider_id });
    const provider = builtinProviderById.get(config.pi_provider_id);
    if (!provider) throw new Error(`Unknown built-in Pi provider: ${config.pi_provider_id}`);
    const providerModels = provider.getModels();
    const builtinModel = providerModels.find((candidate) => candidate.id === config.model_id);
    const modelTemplate = builtinModel ?? providerModels[0];
    if (!modelTemplate) {
      throw new Error(`Built-in Pi provider ${config.pi_provider_id} has no protocol template.`);
    }
    const defaultBaseUrl = provider.baseUrl ?? modelTemplate.baseUrl;
    const isolateReasoningReplay = isCustomResponsesEndpoint(
      modelTemplate,
      config.base_url,
      defaultBaseUrl,
    );
    const customBaseUrlModelOverrides = isolateReasoningReplay
      ? {
          // Custom Responses endpoints must not inherit official prompt-cache capabilities.
          compat: {
            ...(modelTemplate.compat ?? {}),
            supportsExplicitPromptCacheMode: false,
          },
        }
      : {};
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
      ...(config.thinking_level_map
        ? {
            thinkingLevelMap: {
              ...(builtinModel ? modelTemplate.thinkingLevelMap ?? {} : {}),
              ...config.thinking_level_map,
            },
          }
        : {}),
      ...customBaseUrlModelOverrides,
      ...(config.base_url ? { baseUrl: config.base_url } : {}),
      headers: {
        ...modelTemplate.headers,
        ...config.custom_headers,
      },
    } as Model<string>;
    return { models, model, provider, isolateReasoningReplay, credentialStore };
  }

  const models = createModels();
  const model = createAetherModel(config);
  bridgeDebug("build_models_path", { path: "custom", pi_api: config.pi_api });
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
  return { models, model, provider, isolateReasoningReplay: false };
}

async function buildModelRuntime(config: ModelConfig): Promise<{
  modelRuntime: ModelRuntime;
  model: Model<string>;
  isolateReasoningReplay: boolean;
  credentialStore?: BridgeCredentialStore;
}> {
  const startedAt = Date.now();
  bridgeDebug("model_runtime_create_start", {
    provider_type: config.provider_type,
    model_id: config.model_id,
  });
  const built = buildModels(config);
  const modelRuntime = await ModelRuntime.create({
    credentials: built.credentialStore,
    modelsPath: null,
    modelsStore: new InMemoryModelsStore(),
    allowModelNetwork: false,
    refreshOnCreate: false,
  });
  modelRuntime.registerNativeProvider(built.provider);
  bridgeDebug("model_runtime_create_done", {
    provider_type: config.provider_type,
    model_id: config.model_id,
    elapsed_ms: elapsedMillis(startedAt),
  });
  return {
    modelRuntime,
    model: built.model,
    isolateReasoningReplay: built.isolateReasoningReplay,
    credentialStore: built.credentialStore,
  };
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
  // Keep the raw off signal out of native transports. They disable thinking by
  // omitting the reasoning level, while OpenAI-compatible transports still read
  // model.thinkingLevelMap.off when constructing their provider-specific payload.
  const reasoning = thinkingLevel && thinkingLevel !== "off"
    ? clampThinkingLevel(model, thinkingLevel)
    : undefined;
  if (reasoning) {
    options.reasoning = reasoning as SimpleStreamOptions["reasoning"];
  }
  return options;
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
  const toolRequestId = asString(payload.tool_request_id).trim();
  const pending = toolRequestId ? pendingHostToolRequests.get(toolRequestId) : undefined;
  if (!pending) {
    bridgeDebug("host_tool_result_orphaned", {
      tool_request_id: toolRequestId,
      tool_name: asString(payload.tool_name),
    });
    return false;
  }
  pendingHostToolRequests.delete(toolRequestId);
  bridgeDebug("host_tool_result_accepted", {
    tool_request_id: toolRequestId,
    tool_name: asString(payload.tool_name),
    is_error: asBoolean(payload.is_error, false),
  });
  applyRuntimeToolResult(payload);
  pending.resolve(hostToolResultFromPayload(payload));
  return true;
}

function applyHostToolProgress(payload: JsonObject): boolean {
  const toolRequestId = asString(payload.tool_request_id).trim();
  const pending = toolRequestId ? pendingHostToolRequests.get(toolRequestId) : undefined;
  if (!pending) {
    bridgeDebug("host_tool_progress_orphaned", { tool_request_id: toolRequestId });
    return false;
  }
  pending.onUpdate?.(hostToolResultFromPayload(payload));
  return true;
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
  const reasoning = asString(payload.reasoning).trim().toLowerCase();
  if (!reasoning) return undefined;
  const normalized = reasoning === "none" ? "off" : reasoning;
  if (["off", "minimal", "low", "medium", "high", "xhigh", "max"].includes(normalized)) {
    return normalized as "off" | "minimal" | "low" | "medium" | "high" | "xhigh" | "max";
  }
  return undefined;
}

function modelConfigSignature(config: ModelConfig): string {
  return JSON.stringify(config);
}

function hostToolSignature(rawTools: unknown): string {
  return JSON.stringify(normalizeHostToolDefinitions(rawTools));
}

function latestAssistantMessage(messages: AgentMessage[]): AssistantMessage | undefined {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const message = messages[index];
    if (message.role === "assistant") return message;
  }
  return undefined;
}

const AETHER_HOST_TOOL_NAMES = new Set([
  "browser",
  "aether_config_get",
  "aether_config_set",
  "aether_skill_manage",
  "aether_termux_manage",
  "aether_agent_mode_manage",
  "aether_scheduled_task_manage",
  "aether_extension_manage",
  "aether_developer_manage",
  "aether_runtime_manage",
  "agent_display",
]);

function runtimeForPayload(payload: JsonObject): "alpine" | "termux" {
  const explicit = asString(payload.runtime, asString(payload.runtime_id)).trim().toLowerCase();
  if (explicit === "termux") return "termux";
  if (explicit === "alpine") return "alpine";
  return asString(payload.platform).trim().toLowerCase() === "ios" ? "alpine" : "alpine";
}

function platformForPayload(payload: JsonObject): "android" | "ios" {
  return asString(payload.platform).trim().toLowerCase() === "ios" ? "ios" : "android";
}

function activeNativeToolNames(runtime: "alpine" | "termux"): string[] {
  return runtime === "termux"
    ? ["read", "bash", "edit", "write"]
    : ["read", "bash", "edit", "write", "grep", "find", "ls"];
}

function allowedHostToolDefinitions(rawTools: unknown, platform: "android" | "ios"): HostToolDefinition[] {
  return normalizeHostToolDefinitions(rawTools).filter((definition) => {
    if (!AETHER_HOST_TOOL_NAMES.has(definition.name)) return false;
    if (platform === "ios") {
      return new Set([
        "browser",
        "aether_config_get",
        "aether_config_set",
        "aether_skill_manage",
        "aether_extension_manage",
        "aether_developer_manage",
      ]).has(definition.name);
    }
    return true;
  });
}

function requestAgentHostTool(
  state: AgentSessionState,
  definition: HostToolDefinition,
  toolCallId: string,
  args: unknown,
  signal: AbortSignal | undefined,
  onUpdate: ((partial: AgentToolResult<JsonObject>) => void) | undefined,
): Promise<AgentToolResult<JsonObject>> {
  const runRequestId = state.currentRequestId;
  if (!runRequestId) throw new Error(`Host tool ${definition.name} was called outside an active turn.`);
  const toolRequestId = `host-tool-${Date.now()}-${++hostToolCounter}`;
  const startedAt = Date.now();
  bridgeDebug("host_tool_request_sent", {
    tool_request_id: toolRequestId,
    tool_call_id: toolCallId,
    tool_name: definition.name,
    session_id: state.sessionId,
    run_request_id: runRequestId,
  });
  return new Promise((resolve, reject) => {
    const abort = () => {
      if (!pendingHostToolRequests.delete(toolRequestId)) return;
      bridgeDebug("host_tool_request_aborted", {
        tool_request_id: toolRequestId,
        tool_name: definition.name,
        elapsed_ms: elapsedMillis(startedAt),
      });
      reject(new Error(`Host tool ${definition.name} was aborted.`));
    };
    signal?.addEventListener("abort", abort, { once: true });
    pendingHostToolRequests.set(toolRequestId, {
      sessionId: state.sessionId,
      resolve: (result) => {
        signal?.removeEventListener("abort", abort);
        bridgeDebug("host_tool_request_resolved", {
          tool_request_id: toolRequestId,
          tool_name: definition.name,
          elapsed_ms: elapsedMillis(startedAt),
        });
        resolve(result);
      },
      reject: (error) => {
        signal?.removeEventListener("abort", abort);
        bridgeDebug("host_tool_request_rejected", {
          tool_request_id: toolRequestId,
          tool_name: definition.name,
          error: errorMessageWithCause(error),
          elapsed_ms: elapsedMillis(startedAt),
        });
        reject(error);
      },
      onUpdate,
    });
    writeEvent(runRequestId, "host_tool_request", {
      session_id: state.sessionId,
      tool_request_id: toolRequestId,
      tool_call_id: toolCallId,
      tool_name: definition.name,
      arguments: normalizeToolArguments(args),
      arguments_json: JSON.stringify(normalizeToolArguments(args)),
      execution_mode: definition.execution_mode ?? "parallel",
    });
  });
}

function createAgentHostToolDefinition(
  state: AgentSessionState,
  definition: HostToolDefinition,
): ToolDefinition<any, any, any> {
  return {
    name: definition.name,
    label: definition.name,
    description: definition.description,
    parameters: hostToolSchema(definition),
    executionMode: definition.execution_mode,
    execute: (toolCallId, args, signal, onUpdate) =>
      requestAgentHostTool(state, definition, toolCallId, args, signal, onUpdate),
  };
}

function requestRuntimeOperation(
  state: AgentSessionState,
  kind: string,
  payload: JsonObject,
  options: { signal?: AbortSignal; onChunk?: (chunk: Buffer) => void; input?: Buffer } = {},
): Promise<JsonObject> {
  const requestId = state.currentRequestId;
  if (!requestId) throw new Error(`Runtime operation ${kind} was called outside an active turn.`);
  const operationId = `runtime-op-${Date.now()}-${++runtimeOperationCounter}`;
  const startedAt = Date.now();
  bridgeDebug("runtime_op_request_sent", {
    operation_id: operationId,
    kind,
    session_id: state.sessionId,
    input_bytes: options.input?.length ?? 0,
  });
  return new Promise((resolve, reject) => {
    const abort = () => {
      if (!pendingRuntimeOperations.delete(operationId)) return;
      bridgeDebug("runtime_op_aborted", {
        operation_id: operationId,
        kind,
        elapsed_ms: elapsedMillis(startedAt),
      });
      writeEvent(requestId, "runtime_op_cancel", {
        operation_id: operationId,
        session_id: state.sessionId,
        runtime: state.runtime,
      });
      reject(new Error(`Runtime operation ${kind} was aborted.`));
    };
    options.signal?.addEventListener("abort", abort, { once: true });
    pendingRuntimeOperations.set(operationId, {
      sessionId: state.sessionId,
      onChunk: options.onChunk,
      resolve: (result) => {
        options.signal?.removeEventListener("abort", abort);
        bridgeDebug("runtime_op_resolved", {
          operation_id: operationId,
          kind,
          elapsed_ms: elapsedMillis(startedAt),
        });
        resolve(result);
      },
      reject: (error) => {
        options.signal?.removeEventListener("abort", abort);
        bridgeDebug("runtime_op_rejected", {
          operation_id: operationId,
          kind,
          error: errorMessageWithCause(error),
          elapsed_ms: elapsedMillis(startedAt),
        });
        reject(error);
      },
    });
    const inputChunks = options.input
      ? Array.from({ length: Math.ceil(options.input.length / RUNTIME_OPERATION_CHUNK_BYTES) }, (_, index) =>
          options.input!.subarray(index * RUNTIME_OPERATION_CHUNK_BYTES, (index + 1) * RUNTIME_OPERATION_CHUNK_BYTES))
      : [];
    writeEvent(requestId, "runtime_op_request", {
      operation_id: operationId,
      session_id: state.sessionId,
      runtime: state.runtime,
      kind,
      payload,
      input_chunk_count: inputChunks.length,
      input_byte_count: options.input?.length ?? 0,
    });
    inputChunks.forEach((chunk, sequence) => {
      writeEvent(requestId, "runtime_op_chunk", {
        operation_id: operationId,
        session_id: state.sessionId,
        runtime: state.runtime,
        direction: "input",
        sequence,
        data_base64: chunk.toString("base64"),
        final: sequence === inputChunks.length - 1,
      });
    });
  });
}

function runtimeOperationChunk(payload: JsonObject): boolean {
  const operationId = asString(payload.operation_id).trim();
  const pending = pendingRuntimeOperations.get(operationId);
  if (!pending) return false;
  const encoded = asString(payload.data_base64).trim();
  if (encoded) pending.onChunk?.(Buffer.from(encoded, "base64"));
  return true;
}

function runtimeOperationResult(payload: JsonObject): boolean {
  const operationId = asString(payload.operation_id).trim();
  const pending = pendingRuntimeOperations.get(operationId);
  if (!pending) {
    bridgeDebug("runtime_op_result_orphaned", { operation_id: operationId });
    return false;
  }
  pendingRuntimeOperations.delete(operationId);
  bridgeDebug("runtime_op_result_received", {
    operation_id: operationId,
    ok: asBoolean(payload.ok, true),
  });
  if (!asBoolean(payload.ok, true)) {
    pending.reject(new Error(asString(payload.error, "Runtime operation failed.")));
  } else {
    pending.resolve(asObject(payload.result));
  }
  return true;
}

function nodeTemporaryOutputPath(filePath: string): boolean {
  const relative = path.relative(os.tmpdir(), filePath);
  return !relative.startsWith(`..${path.sep}`) && relative !== ".." &&
    path.basename(filePath).startsWith("pi-bash-");
}

function runtimePath(state: AgentSessionState, absolutePath: string): string {
  if (state.runtime !== "termux" || nodeTemporaryOutputPath(absolutePath)) return absolutePath;
  const relative = path.relative(state.workspaceDirectory, absolutePath);
  if (relative === "" || (!relative.startsWith(`..${path.sep}`) && !path.isAbsolute(relative))) {
    return path.resolve(state.termuxWorkspaceDirectory, relative);
  }
  return absolutePath;
}

async function detectLocalImageMimeType(absolutePath: string): Promise<string | undefined> {
  const handle = await fs.open(absolutePath, "r");
  try {
    const bytes = Buffer.alloc(16);
    const { bytesRead } = await handle.read(bytes, 0, bytes.length, 0);
    const header = bytes.subarray(0, bytesRead);
    if (header.subarray(0, 8).equals(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]))) return "image/png";
    if (header[0] === 0xff && header[1] === 0xd8 && header[2] === 0xff) return "image/jpeg";
    if (header.subarray(0, 6).toString("ascii") === "GIF87a" || header.subarray(0, 6).toString("ascii") === "GIF89a") return "image/gif";
    if (header.subarray(0, 2).toString("ascii") === "BM") return "image/bmp";
    if (header.subarray(0, 4).toString("ascii") === "RIFF" && header.subarray(8, 12).toString("ascii") === "WEBP") return "image/webp";
    return undefined;
  } finally {
    await handle.close();
  }
}

function termuxReadOperations(state: AgentSessionState): ReadOperations {
  return {
    access: async (absolutePath) => {
      if (nodeTemporaryOutputPath(absolutePath)) {
        await fs.access(absolutePath);
        return;
      }
      await requestRuntimeOperation(state, "access", { path: absolutePath, mode: "read" });
    },
    readFile: async (absolutePath) => {
      if (nodeTemporaryOutputPath(absolutePath)) return fs.readFile(absolutePath);
      const chunks: Buffer[] = [];
      const result = await requestRuntimeOperation(
        state,
        "readFile",
        { path: absolutePath },
        { onChunk: (chunk) => chunks.push(chunk) },
      );
      return chunks.length > 0
        ? Buffer.concat(chunks)
        : Buffer.from(asString(result.data_base64), "base64");
    },
    detectImageMimeType: async (absolutePath) => {
      if (nodeTemporaryOutputPath(absolutePath)) return undefined;
      const result = await requestRuntimeOperation(state, "detectMime", { path: absolutePath });
      return asString(result.mime_type).trim() || undefined;
    },
  };
}

function termuxEditOperations(state: AgentSessionState): EditOperations {
  const read = termuxReadOperations(state);
  return {
    access: read.access,
    readFile: read.readFile,
    writeFile: async (absolutePath, content) => {
      await requestRuntimeOperation(
        state,
        "writeFile",
        { path: absolutePath, atomic: true },
        { input: Buffer.from(content, "utf8") },
      );
    },
  };
}

function termuxWriteOperations(state: AgentSessionState): WriteOperations {
  return {
    mkdir: async (directory) => {
      await requestRuntimeOperation(state, "mkdir", { path: directory, recursive: true });
    },
    writeFile: async (absolutePath, content) => {
      await requestRuntimeOperation(
        state,
        "writeFile",
        { path: absolutePath, atomic: true },
        { input: Buffer.from(content, "utf8") },
      );
    },
  };
}

function termuxBashOperations(state: AgentSessionState): BashOperations {
  return {
    exec: async (command, cwd, options) => {
      const result = await requestRuntimeOperation(
        state,
        "bash",
        {
          command,
          cwd,
          timeout_seconds: options.timeout,
          env: options.env ?? {},
        },
        { signal: options.signal, onChunk: options.onData },
      );
      const exitCode = result.exit_code;
      return { exitCode: typeof exitCode === "number" ? exitCode : null };
    },
  };
}

function dynamicReadOperations(state: AgentSessionState): ReadOperations {
  return {
    access: async (absolutePath) => {
      const resolved = runtimePath(state, absolutePath);
      if (state.runtime === "termux" && !nodeTemporaryOutputPath(resolved)) {
        await termuxReadOperations(state).access(resolved);
      } else {
        await fs.access(resolved);
      }
    },
    readFile: async (absolutePath) => {
      const resolved = runtimePath(state, absolutePath);
      return state.runtime === "termux" && !nodeTemporaryOutputPath(resolved)
        ? termuxReadOperations(state).readFile(resolved)
        : fs.readFile(resolved);
    },
    detectImageMimeType: async (absolutePath) => {
      const resolved = runtimePath(state, absolutePath);
      return state.runtime === "termux" && !nodeTemporaryOutputPath(resolved)
        ? termuxReadOperations(state).detectImageMimeType?.(resolved)
        : detectLocalImageMimeType(resolved);
    },
  };
}

function dynamicEditOperations(state: AgentSessionState): EditOperations {
  const read = dynamicReadOperations(state);
  return {
    access: read.access,
    readFile: read.readFile,
    writeFile: async (absolutePath, content) => {
      const resolved = runtimePath(state, absolutePath);
      if (state.runtime === "termux") {
        await termuxEditOperations(state).writeFile(resolved, content);
      } else {
        await fs.writeFile(resolved, content, "utf8");
      }
    },
  };
}

function dynamicWriteOperations(state: AgentSessionState): WriteOperations {
  return {
    mkdir: async (directory) => {
      const resolved = runtimePath(state, directory);
      if (state.runtime === "termux") {
        await termuxWriteOperations(state).mkdir(resolved);
      } else {
        await fs.mkdir(resolved, { recursive: true });
      }
    },
    writeFile: async (absolutePath, content) => {
      const resolved = runtimePath(state, absolutePath);
      if (state.runtime === "termux") {
        await termuxWriteOperations(state).writeFile(resolved, content);
      } else {
        await fs.writeFile(resolved, content, "utf8");
      }
    },
  };
}

function dynamicBashOperations(state: AgentSessionState): BashOperations {
  const local = createLocalBashOperations();
  return {
    exec: (command, cwd, options) => state.runtime === "termux"
      ? termuxBashOperations(state).exec(command, runtimePath(state, cwd), options)
      : local.exec(command, cwd, options),
  };
}

function nativeToolDefinitions(state: AgentSessionState): ToolDefinition<any, any, any>[] {
  const cwd = state.workspaceDirectory;
  return [
    createReadToolDefinition(cwd, { operations: dynamicReadOperations(state) }),
    createBashToolDefinition(cwd, { operations: dynamicBashOperations(state) }),
    createEditToolDefinition(cwd, { operations: dynamicEditOperations(state) }),
    createWriteToolDefinition(cwd, { operations: dynamicWriteOperations(state) }),
    createGrepToolDefinition(cwd),
    createFindToolDefinition(cwd),
    createLsToolDefinition(cwd),
  ];
}

function applyRuntimeToolResult(payload: JsonObject): void {
  if (asString(payload.tool_name) !== "aether_runtime_manage" || asBoolean(payload.is_error, false)) return;
  const sessionId = asString(payload.session_id).trim();
  const state = agentSessions.get(sessionId);
  if (!state) return;
  const raw = asString(payload.raw_output_json, asString(payload.output_json));
  let result: JsonObject;
  try {
    result = asObject(JSON.parse(raw));
  } catch {
    return;
  }
  if (!asBoolean(result.ok, false) || asString(result.action) !== "set") return;
  const runtime = asString(result.runtime).trim();
  if (runtime !== "alpine" && runtime !== "termux") return;
  state.runtime = runtime;
  state.session.sessionManager.appendCustomEntry("aether_runtime", {
    runtime,
    cwd: runtime === "termux" ? state.termuxWorkspaceDirectory : state.workspaceDirectory,
  });
  setActiveSessionTools(state);
}

function sessionSettings(payload: JsonObject, config: ModelConfig): SettingsManager {
  return SettingsManager.inMemory({
    defaultProvider: config.pi_provider_id,
    defaultModel: config.model_id,
    defaultThinkingLevel: thinkingLevelFor(payload) ?? "off",
    enableSkillCommands: true,
    compaction: { enabled: true },
    retry: {
      enabled: true,
      maxRetries: Math.max(0, asNumber(payload.max_retries, config.max_retries ?? DEFAULT_AGENT_RETRY_MAX_RETRIES)),
      provider: {
        timeoutMs: asNumber(payload.timeout_ms, config.timeout_ms ?? 360000),
        maxRetries: 0,
        maxRetryDelayMs: asNumber(payload.max_retry_delay_ms, config.max_retry_delay_ms ?? 60000),
      },
    },
    images: { autoResize: true, blockImages: false },
  }, { projectTrusted: asBoolean(payload.workspace_trusted, false) });
}

function stringArray(value: unknown): string[] {
  return Array.isArray(value)
    ? value.filter((entry): entry is string => typeof entry === "string" && entry.trim().length > 0)
    : [];
}

async function createSessionManager(
  sessionId: string,
  cwd: string,
  payload: JsonObject,
): Promise<{ manager: SessionManager; existed: boolean }> {
  const explicitFile = asString(payload.session_file).trim();
  if (explicitFile) return { manager: SessionManager.open(explicitFile, undefined, cwd), existed: true };
  const sessionDirectory = asString(
    payload.session_directory,
    path.join(os.homedir(), ".aether", "agent-sessions"),
  );
  await fs.mkdir(sessionDirectory, { recursive: true });
  const suffix = `_${sessionId}.jsonl`;
  const existing = (await fs.readdir(sessionDirectory))
    .filter((entry) => entry.endsWith(suffix))
    .sort()
    .at(-1);
  if (existing) {
    return {
      manager: SessionManager.open(path.join(sessionDirectory, existing), sessionDirectory, cwd),
      existed: true,
    };
  }
  return {
    manager: SessionManager.create(cwd, sessionDirectory, { id: sessionId }),
    existed: false,
  };
}

function extensionUiContext(): ExtensionUIContext {
  const unsupported = async () => undefined;
  return {
    select: async (title: string, options: string[]) => {
      const result = await requestAetherHost("pi_extension_select", { title, options });
      return asString(result.value).trim() || undefined;
    },
    confirm: async (title: string, message: string) => {
      const result = await requestAetherHost("pi_extension_confirm", { title, message });
      return asBoolean(result.value, false);
    },
    input: async (title: string, placeholder?: string) => {
      const result = await requestAetherHost("pi_extension_input", { title, placeholder: placeholder ?? "" });
      return asString(result.value) || undefined;
    },
    notify: (message: string, type?: "info" | "warning" | "error") => {
      void requestAetherHost("pi_extension_notify", { message, type: type ?? "info" });
    },
    onTerminalInput: () => () => undefined,
    setStatus: () => undefined,
    setWorkingMessage: () => undefined,
    setWorkingVisible: () => undefined,
    setWorkingIndicator: () => undefined,
    setHiddenThinkingLabel: () => undefined,
    setWidget: () => undefined,
    setFooter: () => undefined,
    setHeader: () => undefined,
    setTitle: () => undefined,
    custom: unsupported,
    setEditorText: () => undefined,
    getEditorText: () => "",
    editor: () => undefined,
    setEditorComponent: () => undefined,
    getTheme: () => undefined,
    getAllThemes: () => [],
    setTheme: () => ({ success: false, error: "Pi TUI components are unavailable in Aether." }),
    getToolsExpanded: () => false,
    setToolsExpanded: () => undefined,
  } as unknown as ExtensionUIContext;
}

const aetherResponsesCompatibilityExtensionFactory: ExtensionFactory = (pi) => {
  pi.on("context", (event): { messages: typeof event.messages } => ({
    // Gateway-routed encrypted reasoning may not be decryptable by the next upstream credential.
    messages: isolateResponsesReasoningReplay(event.messages),
  }));
};

const aetherChromeExtensionFactory: ExtensionFactory = (pi) => {
  pi.registerTool({
    name: "browser",
    label: "Browser",
    description: "Control Aether's Chromium browser. Prefer CSS selectors and DOM-reading actions; use normalized coordinates only as a fallback.",
    promptSnippet: "control the optional browser",
    executionMode: "sequential",
    parameters: Type.Object({
      action: Type.Union([
        Type.Literal("start"), Type.Literal("status"), Type.Literal("navigate"), Type.Literal("click"),
        Type.Literal("type"), Type.Literal("get_text"), Type.Literal("scroll"), Type.Literal("get_page_info"),
        Type.Literal("execute_js"), Type.Literal("find_elements"), Type.Literal("get_readable"),
        Type.Literal("get_backbone"), Type.Literal("back"), Type.Literal("forward"), Type.Literal("reload"),
        Type.Literal("screenshot"), Type.Literal("wait_for_dom_stable"), Type.Literal("stop"),
      ]),
      url: Type.Optional(Type.String({ description: "For navigate: the URL to open." })),
      selector: Type.Optional(Type.String({ description: "CSS selector for click, type, get_text, scroll, or find_elements." })),
      text: Type.Optional(Type.String({ description: "For type: text to enter." })),
      x: Type.Optional(Type.Integer({ description: "For click fallback: normalized X coordinate from 0 to 1000." })),
      y: Type.Optional(Type.Integer({ description: "For click fallback: normalized Y coordinate from 0 to 1000." })),
      direction: Type.Optional(Type.Union([Type.Literal("up"), Type.Literal("down")])),
      amount: Type.Optional(Type.Integer({ description: "Scroll distance in CSS pixels." })),
      script: Type.Optional(Type.String({ description: "JavaScript source for execute_js." })),
      max_depth: Type.Optional(Type.Integer({ description: "Maximum DOM depth for get_backbone." })),
      timeout: Type.Optional(Type.Integer({ description: "Maximum wait in milliseconds for wait_for_dom_stable." })),
    }),
    execute: async (_toolCallId, params, signal) => {
      if (signal?.aborted) throw new Error("Browser operation was cancelled.");
      const result = await requestAetherHost("aether_chrome_execute", { arguments: params as JsonObject });
      const screenshot = asString(result.screenshot_base64).trim();
      const visible = { ...result };
      delete visible.screenshot_base64;
      const content: Array<TextContent | ImageContent> = [{
        type: "text",
        text: asString(visible.stdout, JSON.stringify(visible)),
      }];
      if (screenshot) {
        content.push({
          type: "image",
          mimeType: asString(result.screenshot_mime_type, "image/jpeg"),
          data: screenshot,
        });
      }
      return { content, details: visible };
    },
  });
};

function setActiveSessionTools(state: AgentSessionState): void {
  const nativeNames = new Set(["read", "bash", "edit", "write", "grep", "find", "ls"]);
  const nonNative = state.session.getActiveToolNames()
    .filter((name) => !nativeNames.has(name))
    .filter((name) => name !== "browser" || state.chromeEnabled || state.platform === "ios");
  state.session.setActiveToolsByName([...activeNativeToolNames(state.runtime), ...nonNative]);
}

function emitAgentSessionEvent(state: AgentSessionState, event: AgentSessionEvent): void {
  const requestId = state.currentRequestId;
  if (!requestId) {
    if (event.type !== "message_update") {
      bridgeDebug("agent_session_event_without_request", {
        event_type: event.type,
        session_id: state.sessionId,
      });
    }
    return;
  }
  switch (event.type) {
    case "message_update":
      if (event.message.role === "assistant" &&
          (event.assistantMessageEvent.type === "text_delta" || event.assistantMessageEvent.type === "thinking_delta")) {
        if (state.firstAssistantEventAtMillis === undefined) {
          state.firstAssistantEventAtMillis = Date.now();
          bridgeDebug("first_assistant_delta", {
            session_id: state.sessionId,
            delta_type: event.assistantMessageEvent.type,
            elapsed_since_turn_start_ms: state.turnStartedAtMillis
              ? state.firstAssistantEventAtMillis - state.turnStartedAtMillis
              : undefined,
          });
        }
        emitStreamEvent(requestId, event.assistantMessageEvent);
      }
      return;
    case "tool_execution_start":
      state.toolArgsById.set(event.toolCallId, event.args);
      writeEvent(requestId, "tool_call_start", toolEventPayload(event.toolCallId, event.toolName, event.args));
      return;
    case "tool_execution_update":
      writeEvent(requestId, "tool_call_delta", toolEventPayload(event.toolCallId, event.toolName, event.args, event.partialResult));
      return;
    case "tool_execution_end":
      writeEvent(requestId, "tool_call_end", toolEventPayload(
        event.toolCallId,
        event.toolName,
        state.toolArgsById.get(event.toolCallId) ?? {},
        event.result,
        event.isError,
      ));
      state.toolArgsById.delete(event.toolCallId);
      return;
    case "auto_retry_start":
      writeEvent(requestId, "assistant_stream_reset", {});
      writeEvent(requestId, "assistant_retry", {
        attempt: event.attempt,
        max_attempts: event.maxAttempts,
        delay_ms: event.delayMs,
        error_message: event.errorMessage,
      });
      return;
    case "compaction_start":
      writeEvent(requestId, "compaction_start", { reason: event.reason });
      return;
    case "compaction_end":
      writeEvent(requestId, "compaction_end", {
        reason: event.reason,
        aborted: event.aborted,
        will_retry: event.willRetry,
        error_message: event.errorMessage ?? "",
      });
      return;
    case "entry_appended":
      writeEvent(requestId, "session_entry_appended", { entry: event.entry });
      return;
    case "agent_settled":
      if (state.pendingRecreate) {
        state.pendingRecreate = false;
        state.pendingReload = false;
        void closeNativeAgentSession(state.sessionId).catch((error) => {
          stderr.write(`pi session recreation cleanup failed: ${errorMessageWithCause(error)}\n`);
        });
      } else if (state.pendingReload) {
        state.pendingReload = false;
        void state.session.reload().catch((error) => {
          stderr.write(`pi session reload failed: ${errorMessageWithCause(error)}\n`);
        });
      }
      return;
    default:
      return;
  }
}

function nativeExtensionPathIsDisabled(candidatePath: string, disabledPaths: Set<string>): boolean {
  const candidate = path.resolve(candidatePath);
  return [...disabledPaths].some((disabledPath) => {
    const relative = path.relative(disabledPath, candidate);
    return relative === "" || (
      !relative.startsWith(`..${path.sep}`) &&
      relative !== ".." &&
      !path.isAbsolute(relative)
    );
  });
}

async function resolveNativeExtensionSet(
  workspaceDirectory: string,
  configuredExtensionPaths: string[],
  loadOptions: NativeExtensionLoadOptions,
): Promise<{ extensionPaths: string[]; signature: string }> {
  const disabledPaths = new Set(
    loadOptions.disabledExtensionPaths.map((entry) => path.resolve(entry)),
  );
  const discoveredPaths = discoverAetherExtensionPaths(
    workspaceDirectory,
    configuredExtensionPaths,
  ).filter((candidate) => !nativeExtensionPathIsDisabled(candidate, disabledPaths));
  const packagePaths = await discoverPackageExtensionPaths(
    workspaceDirectory,
    new Set(loadOptions.disabledPackageSources),
  );
  const extensionPaths = [...new Set([...discoveredPaths, ...packagePaths])].sort();
  return {
    extensionPaths,
    signature: JSON.stringify(extensionPaths),
  };
}

async function createNativeAgentSession(
  sessionId: string,
  payload: JsonObject,
  config: ModelConfig,
  history: AgentMessage[],
): Promise<AgentSessionState> {
  const workspaceDirectory = asString(payload.workspace_directory, process.cwd()) || process.cwd();
  const termuxWorkspaceDirectory = asString(payload.termux_workspace_directory, workspaceDirectory) || workspaceDirectory;
  let runtime = runtimeForPayload(payload);
  const platform = platformForPayload(payload);
  const built = await buildModelRuntime(config);
  const settingsManager = sessionSettings(payload, config);
  const agentDir = asString(payload.agent_directory, path.join(os.homedir(), ".pi", "agent"));
  const configuredExtensionPaths = stringArray(payload.extension_paths);
  // Extension files are part of the session's tool registry. Include the
  // resolved set in the reuse key so installing/enabling a package cannot
  // leave an existing session with a stale tool list.
  const { extensionPaths: additionalExtensionPaths, signature: extensionSignature } =
    await resolveNativeExtensionSet(
      workspaceDirectory,
      configuredExtensionPaths,
      {
        disabledExtensionPaths: stringArray(payload.disabled_extension_paths),
        disabledPackageSources: stringArray(payload.disabled_package_sources),
      },
    );
  const resourceLoader = new DefaultResourceLoader({
    cwd: workspaceDirectory,
    agentDir,
    settingsManager,
    additionalExtensionPaths,
    extensionFactories: [
      ...(built.isolateReasoningReplay ? [aetherResponsesCompatibilityExtensionFactory] : []),
      ...(platform === "android" ? [aetherChromeExtensionFactory] : []),
    ],
    additionalSkillPaths: stringArray(payload.skill_paths),
    appendSystemPrompt: [asString(payload.system_prompt)].filter(Boolean),
  });
  await resourceLoader.reload({
    resolveProjectTrust: async () => asBoolean(payload.workspace_trusted, false),
  });
  const { manager, existed } = await createSessionManager(sessionId, workspaceDirectory, payload);
  if (!existed) history.forEach((message) => manager.appendMessage(message as never));
  if (existed) {
    const runtimeEntry = manager.getEntries().findLast((entry) =>
      entry.type === "custom" && entry.customType === "aether_runtime"
    );
    if (runtimeEntry?.type === "custom") {
      const persistedRuntime = asString(asObject(runtimeEntry.data).runtime);
      if (persistedRuntime === "alpine" || persistedRuntime === "termux") runtime = persistedRuntime;
    }
  }
  const state = {
    sessionId,
    configSignature: modelConfigSignature(config),
    toolSignature: hostToolSignature(allowedHostToolDefinitions(payload.host_tools, platform)),
    extensionSignature,
    skillSignature: JSON.stringify(stringArray(payload.skill_paths).sort()),
    workspaceDirectory,
    termuxWorkspaceDirectory,
    runtime,
    platform,
    chromeEnabled: platform === "android" && asBoolean(payload.chrome_enabled, false),
    modelRuntime: built.modelRuntime,
    model: built.model,
    credentialStore: built.credentialStore,
    session: undefined as unknown as AgentSession,
    resourceLoader,
    settingsManager,
    configuredExtensionPaths,
    pendingReload: false,
    pendingRecreate: false,
    currentRequestId: "",
    toolArgsById: new Map<string, unknown>(),
    lastAccessedAt: Date.now(),
  } satisfies AgentSessionState;
  const customTools = [
    ...nativeToolDefinitions(state),
    ...allowedHostToolDefinitions(payload.host_tools, platform).map((tool) =>
      createAgentHostToolDefinition(state, tool),
    ),
  ];
  const created = await createAgentSession({
    cwd: workspaceDirectory,
    agentDir,
    modelRuntime: built.modelRuntime,
    model: built.model,
    thinkingLevel: thinkingLevelFor(payload),
    resourceLoader,
    settingsManager,
    sessionManager: manager,
    noTools: "builtin",
    customTools,
  });
  state.session = created.session;
  state.session.subscribe((event) => emitAgentSessionEvent(state, event));
  await state.session.bindExtensions({
    uiContext: extensionUiContext(),
    mode: "rpc",
    onError: (error) => {
      if (state.currentRequestId) {
        writeEvent(state.currentRequestId, "extension_error", {
          extension_path: error.extensionPath,
          event: error.event,
          error: error.error,
        });
      }
    },
  });
  setActiveSessionTools(state);
  agentSessions.set(sessionId, state);
  return state;
}

async function listDiscoveredSkills(payload: JsonObject): Promise<JsonObject> {
  const workspaceDirectory = asString(payload.workspace_directory, process.cwd()) || process.cwd();
  const agentDir = asString(payload.agent_directory, path.join(os.homedir(), ".pi", "agent"));
  const settingsManager = SettingsManager.create(workspaceDirectory, agentDir, {
    projectTrusted: asBoolean(payload.workspace_trusted, true),
  });
  const resourceLoader = new DefaultResourceLoader({
    cwd: workspaceDirectory,
    agentDir,
    settingsManager,
    additionalSkillPaths: stringArray(payload.skill_paths),
    noExtensions: true,
    noPromptTemplates: true,
    noThemes: true,
    noContextFiles: true,
  });
  await resourceLoader.reload({
    resolveProjectTrust: async () => asBoolean(payload.workspace_trusted, true),
  });
  const managedRoot = path.resolve(workspaceDirectory, ".aether", "skills");
  const skills = resourceLoader.getSkills().skills
    .filter((skill) => {
      const relative = path.relative(managedRoot, path.resolve(skill.filePath));
      return skill.sourceInfo.origin !== "package" &&
        relative !== "" &&
        (relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative));
    })
    .map((skill) => ({
      name: skill.name,
      description: skill.description,
      file_path: skill.filePath,
      base_dir: skill.baseDir,
      source: skill.sourceInfo.source,
      scope: skill.sourceInfo.scope,
      origin: skill.sourceInfo.origin,
    }));
  return { skills };
}

function rejectPendingHostToolsForAgentSession(sessionId: string, message: string): void {
  for (const [toolRequestId, pending] of pendingHostToolRequests) {
    if (pending.sessionId !== sessionId) continue;
    pending.reject(new Error(message));
    pendingHostToolRequests.delete(toolRequestId);
  }
}

function nativeBridgePrompt(rawMessage: unknown): { text: string; images: ImageContent[] } {
  const messages = normalizeMessages([rawMessage]);
  const message = messages[0];
  if (!message || message.role !== "user") throw new Error("A user message is required.");
  if (typeof message.content === "string") return { text: message.content, images: [] };
  return {
    text: message.content
      .filter((part): part is TextContent => part.type === "text")
      .map((part) => part.text)
      .join("\n"),
    images: message.content.filter((part): part is ImageContent => part.type === "image"),
  };
}

async function closeNativeAgentSession(sessionId: string): Promise<boolean> {
  const state = agentSessions.get(sessionId);
  if (!state) return false;
  agentSessions.delete(sessionId);
  rejectPendingHostToolsForAgentSession(sessionId, "Host tool execution ended with the Pi session.");
  for (const [operationId, pending] of pendingRuntimeOperations) {
    if (pending.sessionId !== sessionId) continue;
    pending.reject(new Error("Runtime operation ended with the Pi session."));
    pendingRuntimeOperations.delete(operationId);
  }
  await state.session.abort().catch(() => undefined);
  state.session.dispose();
  return true;
}

function nativeSessionFromPayload(payload: JsonObject): AgentSessionState {
  const sessionId = asString(payload.session_id).trim();
  if (!sessionId) throw new Error("session_id is required for Pi AgentSession operations.");
  const state = agentSessions.get(sessionId);
  if (!state) throw new Error(`Unknown Pi session: ${sessionId}`);
  return state;
}

async function ensureNativeSessionForRequest(payload: JsonObject): Promise<AgentSessionState> {
  const sessionId = asString(payload.session_id).trim();
  if (!sessionId) throw new Error("session_id is required for Pi AgentSession operations.");
  const existing = agentSessions.get(sessionId);
  if (existing) return existing;
  const config = normalizeModelConfig(payload.model_config ?? defaultModelConfig);
  if (!config) throw new Error(`Unknown Pi session: ${sessionId}`);
  return createNativeAgentSession(sessionId, payload, config, []);
}

function nativeSessionPayload(state: AgentSessionState): JsonObject {
  return {
    session_id: state.session.sessionId,
    session_file: state.session.sessionFile ?? "",
    session_leaf_id: state.session.sessionManager.getLeafId() ?? "",
    runtime: state.runtime,
    cwd: state.runtime === "termux" ? state.termuxWorkspaceDirectory : state.workspaceDirectory,
    is_idle: state.session.isIdle,
    is_streaming: state.session.isStreaming,
    is_compacting: state.session.isCompacting,
    active_tools: state.session.getActiveToolNames(),
    tools: state.session.getAllTools().map((tool) => ({
      name: tool.name,
      description: tool.description,
      parameters: tool.parameters,
      prompt_guidelines: tool.promptGuidelines ?? [],
      source_path: tool.sourceInfo.path,
    })),
    entries: state.session.sessionManager.getEntries(),
    tree: state.session.sessionManager.getTree(),
  };
}

async function compactNativeAgentSession(id: string, payload: JsonObject): Promise<JsonObject> {
  const state = await ensureNativeSessionForRequest(payload);
  if (!state.session.isIdle) throw new Error("Cannot manually compact a busy Pi AgentSession.");
  state.currentRequestId = id;
  activeAborters.set(id, () => state.session.abortCompaction());
  try {
    const result = await state.session.compact(asString(payload.custom_instructions).trim() || undefined);
    return { ...nativeSessionPayload(state), compaction: result as unknown as JsonObject };
  } finally {
    activeAborters.delete(id);
    if (state.currentRequestId === id) state.currentRequestId = "";
  }
}

async function navigateNativeAgentSession(id: string, payload: JsonObject): Promise<JsonObject> {
  // The AgentSession map is process-local. Reopen the persisted JSONL session
  // when the bridge was restarted, provided the caller supplies its model
  // configuration (the normal Aether navigation path does).
  const state = await ensureNativeSessionForRequest(payload);
  if (!state.session.isIdle) throw new Error("Cannot navigate a busy Pi AgentSession.");
  const entryId = asString(payload.entry_id).trim();
  if (!entryId && asBoolean(payload.reset, false)) {
    state.session.sessionManager.resetLeaf();
    return { ...nativeSessionPayload(state), navigation: { reset: true } };
  }
  if (!entryId) throw new Error("entry_id is required for Pi session navigation.");
  state.currentRequestId = id;
  activeAborters.set(id, () => state.session.abortBranchSummary());
  try {
    const result = await state.session.navigateTree(entryId, {
      summarize: asBoolean(payload.summarize, false),
      customInstructions: asString(payload.custom_instructions).trim() || undefined,
      replaceInstructions: asBoolean(payload.replace_instructions, false),
      label: asString(payload.label).trim() || undefined,
    });
    return { ...nativeSessionPayload(state), navigation: result };
  } finally {
    activeAborters.delete(id);
    if (state.currentRequestId === id) state.currentRequestId = "";
  }
}

async function reloadNativeAgentSession(payload: JsonObject): Promise<JsonObject> {
  const state = nativeSessionFromPayload(payload);
  if (!state.session.isIdle) {
    state.pendingReload = true;
    return { ...nativeSessionPayload(state), reloaded: false, scheduled: true };
  }
  await state.session.reload();
  return { ...nativeSessionPayload(state), reloaded: true, scheduled: false };
}

function exportNativeAgentSession(payload: JsonObject): JsonObject {
  const state = nativeSessionFromPayload(payload);
  const outputPath = asString(payload.output_path).trim() || undefined;
  return {
    ...nativeSessionPayload(state),
    exported_path: state.session.exportToJsonl(outputPath),
  };
}

async function importNativeAgentSession(payload: JsonObject): Promise<JsonObject> {
  const sessionId = asString(payload.session_id).trim();
  const jsonl = asString(payload.jsonl);
  if (!sessionId || !jsonl.trim()) throw new Error("session_id and jsonl are required.");
  const firstLine = jsonl.split(/\r?\n/, 1)[0] ?? "";
  let header: JsonObject;
  try {
    header = JSON.parse(firstLine) as JsonObject;
  } catch {
    throw new Error("Pi JSONL header is invalid.");
  }
  if (header.type !== "session" || asString(header.id) !== sessionId) {
    throw new Error("Pi JSONL header/session id mismatch.");
  }
  const sessionDirectory = asString(
    payload.session_directory,
    path.join(os.homedir(), ".aether", "agent-sessions"),
  );
  await fs.mkdir(sessionDirectory, { recursive: true });
  const target = path.join(sessionDirectory, `${Date.now()}_${sessionId}.jsonl`);
  const temporary = `${target}.tmp-${process.pid}`;
  await fs.writeFile(temporary, jsonl.endsWith("\n") ? jsonl : `${jsonl}\n`, "utf8");
  await fs.rename(temporary, target);
  await closeNativeAgentSession(sessionId);
  return { imported: true, session_id: sessionId, session_file: target };
}

async function prepareNativeAgentSession(
  payload: JsonObject,
  history: AgentMessage[],
): Promise<{ state: AgentSessionState; reused: boolean }> {
  const config = normalizeModelConfig(payload.model_config ?? defaultModelConfig);
  const sessionId = asString(payload.session_id).trim();
  if (!sessionId) throw new Error("session_id is required for Pi AgentSession.");
  const platform = platformForPayload(payload);
  const signature = hostToolSignature(allowedHostToolDefinitions(payload.host_tools, platform));
  const configuredExtensionPaths = stringArray(payload.extension_paths);
  const workspaceDirectory = asString(payload.workspace_directory, process.cwd()) || process.cwd();
  const { signature: extensionSignature } = await resolveNativeExtensionSet(
    workspaceDirectory,
    configuredExtensionPaths,
    {
      disabledExtensionPaths: stringArray(payload.disabled_extension_paths),
      disabledPackageSources: stringArray(payload.disabled_package_sources),
    },
  );
  const skillSignature = JSON.stringify(stringArray(payload.skill_paths).sort());
  const existing = agentSessions.get(sessionId);
  const reusable = existing &&
    existing.configSignature === modelConfigSignature(config) &&
    existing.toolSignature === signature &&
    existing.extensionSignature === extensionSignature &&
    existing.skillSignature === skillSignature &&
    existing.workspaceDirectory === workspaceDirectory &&
    existing.runtime === runtimeForPayload(payload);
  if (existing && !reusable) await closeNativeAgentSession(sessionId);
  if (!reusable) {
    return { state: await createNativeAgentSession(sessionId, payload, config, history), reused: false };
  }
  existing.lastAccessedAt = Date.now();
  existing.chromeEnabled = platform === "android" && asBoolean(payload.chrome_enabled, false);
  setActiveSessionTools(existing);
  return { state: existing, reused: true };
}

async function runNativeAgentPrompt(
  id: string,
  state: AgentSessionState,
  text: string,
  images: ImageContent[],
): Promise<AssistantMessage> {
  state.currentRequestId = id;
  state.lastAccessedAt = Date.now();
  activeAborters.set(id, () => state.session.abort());
  activeAetherOperationRequestIds.add(id);
  try {
    await aetherOperationContext.run(id, () =>
      state.session.prompt(text, { images: images.length > 0 ? images : undefined }),
    );
    await state.session.waitForIdle();
    const message = latestAssistantMessage(state.session.messages);
    if (!message) throw new Error(`Pi session ${state.sessionId} has no assistant response.`);
    return message;
  } finally {
    activeAborters.delete(id);
    activeAetherOperationRequestIds.delete(id);
    if (state.currentRequestId === id) state.currentRequestId = "";
    state.lastAccessedAt = Date.now();
  }
}

async function runNativeAgentTurn(id: string, payload: JsonObject): Promise<JsonObject> {
  const messages = normalizeMessages(payload.messages);
  const prompt = promptFromLastUserMessage(messages);
  const { state, reused } = await prepareNativeAgentSession(payload, prompt.history);
  const requestedThinkingLevel = thinkingLevelFor(payload) ?? "off";
  state.session.setThinkingLevel(requestedThinkingLevel);
  bridgeDebug("agent_turn_thinking_level", {
    session_id: state.sessionId,
    requested: requestedThinkingLevel,
    effective: state.session.thinkingLevel,
    session_reused: reused,
  });
  const message = await runNativeAgentPrompt(id, state, prompt.text, prompt.images);
  return {
    ...assistantPayload(message),
    ...(await credentialPayload(state.credentialStore)),
    session_id: state.session.sessionId,
    session_file: state.session.sessionFile ?? "",
    session_leaf_id: state.session.sessionManager.getLeafId() ?? "",
    runtime: state.runtime,
    cwd: state.runtime === "termux" ? state.termuxWorkspaceDirectory : state.workspaceDirectory,
    session_reused: reused,
  };
}

async function steerNativeAgentSession(payload: JsonObject): Promise<JsonObject> {
  const state = agentSessions.get(asString(payload.session_id).trim());
  if (!state || !state.session.isStreaming) return { accepted: false };
  const prompt = nativeBridgePrompt(payload.message);
  await state.session.steer(prompt.text, prompt.images);
  return { accepted: true };
}

async function followUpNativeAgentSession(id: string, payload: JsonObject): Promise<JsonObject> {
  const state = agentSessions.get(asString(payload.session_id).trim());
  if (!state) throw new Error(`Unknown Pi session: ${asString(payload.session_id)}`);
  const prompt = nativeBridgePrompt(payload.message);
  if (state.session.isStreaming) {
    await state.session.followUp(prompt.text, prompt.images);
    await state.session.waitForIdle();
    const message = latestAssistantMessage(state.session.messages);
    if (!message) throw new Error(`Pi session ${state.sessionId} has no assistant response.`);
    return assistantPayload(message);
  }
  return assistantPayload(await runNativeAgentPrompt(id, state, prompt.text, prompt.images));
}

async function runSimpleCompletion(id: string, payload: JsonObject, stream: boolean): Promise<JsonObject> {
  const config = normalizeModelConfig(payload.model_config ?? defaultModelConfig);
  const { models, model, isolateReasoningReplay, credentialStore } = buildModels(config);
  const controller = new AbortController();
  activeAborters.set(id, () => controller.abort());
  try {
    const context = buildContext(payload);
    if (isolateReasoningReplay) {
      context.messages = isolateResponsesReasoningReplay(context.messages);
    }
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
  const callbacks = {
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
  } satisfies AuthInteraction;
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

async function closeAgentSessionRequest(payload: JsonObject): Promise<JsonObject> {
  const sessionId = asString(payload.session_id).trim();
  if (!sessionId) throw new Error("session_id is required to close a Pi AgentSession.");
  const state = agentSessions.get(sessionId);
  const sessionFile = state?.session.sessionFile ?? asString(payload.session_file).trim();
  const closed = await closeNativeAgentSession(sessionId);
  let deleted = false;
  if (asBoolean(payload.delete_file, false) && sessionFile) {
    const expectedSuffix = `_${sessionId}.jsonl`;
    if (!path.basename(sessionFile).endsWith(expectedSuffix)) {
      throw new Error("Refusing to delete a Pi session file that does not match the session id.");
    }
    deleted = await fs.unlink(sessionFile).then(() => true).catch((error: NodeJS.ErrnoException) => {
      if (error.code === "ENOENT") return false;
      throw error;
    });
  }
  return { closed, deleted, session_file: sessionFile };
}

function extensionRuntimePayload(state: AgentSessionState): JsonObject {
  const runner = state.session.extensionRunner;
  const loaded = state.resourceLoader.getExtensions();
  return {
    session_id: state.sessionId,
    workspace_directory: state.workspaceDirectory,
    extension_paths: runner.getExtensionPaths(),
    discovered_paths: loaded.extensions.map((extension) => extension.path),
    errors: loaded.errors,
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
    pending_reload: state.pendingReload,
    ui_mode: "rpc",
    custom_tui_supported: false,
  };
}

function extensionSessionFromPayload(payload: JsonObject): AgentSessionState {
  const sessionId = asString(payload.session_id).trim();
  if (!sessionId) throw new Error("session_id is required for Pi extension operations.");
  const state = agentSessions.get(sessionId);
  if (!state) throw new Error(`Unknown Pi session: ${sessionId}`);
  return state;
}

async function listExtensions(payload: JsonObject): Promise<JsonObject> {
  return extensionRuntimePayload(extensionSessionFromPayload(payload));
}

async function reloadExtensions(payload: JsonObject): Promise<JsonObject> {
  const state = extensionSessionFromPayload(payload);
  const scheduled = !state.session.isIdle;
  if (scheduled) state.pendingReload = true;
  else await state.session.reload();
  return {
    ...extensionRuntimePayload(state),
    reloaded: !scheduled,
    scheduled,
  };
}

async function invokeExtensionCommand(payload: JsonObject): Promise<JsonObject> {
  const state = extensionSessionFromPayload(payload);
  const commandName = asString(payload.command).trim().replace(/^\//, "");
  if (!commandName) throw new Error("command is required for Pi extension command invocation.");
  const command = state.session.extensionRunner.getCommand(commandName);
  if (!command) throw new Error(`Unknown Pi extension command: ${commandName}`);
  const context = state.session.extensionRunner.createCommandContext() as ExtensionCommandContext;
  await command.handler(asString(payload.args), context);
  return {
    invoked: true,
    command: commandName,
    pending_reload: state.pendingReload,
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
  const loadOptions = nativeExtensionLoadOptionsFromPayload(payload);
  // Preinstalled packages can provide both Pi and Aether entrypoints. Resolve
  // their npm dependencies before reloading native Pi sessions so both loaders
  // observe the same ready package tree.
  const skipAetherExtensions = asBoolean(payload.skip_aether_extensions, false);
  const aetherReload = skipAetherExtensions
    ? { reloaded: false, errors: [] }
    : await loadAetherAppExtensions(process.cwd(), loadOptions);
  const results: JsonObject[] = [];
  for (const state of [...agentSessions.values()]) {
    const { signature: extensionSignature } = await resolveNativeExtensionSet(
      state.workspaceDirectory,
      state.configuredExtensionPaths,
      loadOptions,
    );
    const requiresRecreation = extensionSignature !== state.extensionSignature;
    const scheduled = !state.session.isIdle;
    if (scheduled) {
      state.pendingReload = true;
      state.pendingRecreate = requiresRecreation;
    } else if (requiresRecreation) {
      await closeNativeAgentSession(state.sessionId);
    } else {
      await state.session.reload();
    }
    results.push({
      session_id: state.sessionId,
      reloaded: !scheduled,
      scheduled,
      recreated_on_next_use: requiresRecreation,
      errors: requiresRecreation ? [] : state.resourceLoader.getExtensions().errors,
    });
  }
  // ResourceLoader keeps healthy extensions active while reporting broken ones as
  // diagnostics. A diagnostic must not make an unrelated import/enable operation
  // fail; an actual reload exception is already propagated above.
  const sessionReloadSucceeded = results.every((result) =>
    result.reloaded === true || result.scheduled === true
  );
  return {
    // Aether Script extensions are reloaded independently. Keep the session
    // operation usable when one unrelated Aether extension remains broken; its
    // details are still returned in aether_reload.errors for diagnostics.
    succeeded: sessionReloadSucceeded,
    session_count: results.length,
    sessions: results,
    aether_reload: aetherReload,
    aether: skipAetherExtensions ? {} : await aetherAppExtensionSnapshot(),
  };
}

function nativeExtensionLoadOptionsFromPayload(payload: JsonObject): {
  disabledExtensionPaths: string[];
  disabledPackageSources: string[];
} {
  const hasOptions = Object.prototype.hasOwnProperty.call(payload, "disabled_extension_paths") ||
    Object.prototype.hasOwnProperty.call(payload, "disabled_package_sources");
  if (hasOptions) {
    currentExtensionLoadOptions = {
      disabledExtensionPaths: stringArray(payload.disabled_extension_paths),
      disabledPackageSources: stringArray(payload.disabled_package_sources),
    };
  }
  return currentExtensionLoadOptions;
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
      nativeExtensionLoadOptionsFromPayload(payload),
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
  const state = sessionId ? agentSessions.get(sessionId) : undefined;
  if (aborter) {
    void Promise.resolve(aborter()).catch((error) => {
      stderr.write(`pi-bridge abort failed: ${error instanceof Error ? error.message : String(error)}\n`);
    });
  } else if (state) {
    void state.session.abort().catch((error) => {
      stderr.write(`pi-bridge abort failed: ${error instanceof Error ? error.message : String(error)}\n`);
    });
  }
  for (const [toolRequestId, pending] of pendingHostToolRequests) {
    if (sessionId && pending.sessionId === sessionId) {
      pending.reject(new Error("Host tool execution aborted with the Pi session."));
      pendingHostToolRequests.delete(toolRequestId);
    }
  }
  for (const [operationId, pending] of pendingRuntimeOperations) {
    if (sessionId && pending.sessionId === sessionId) {
      pending.reject(new Error("Runtime operation aborted with the Pi session."));
      pendingRuntimeOperations.delete(operationId);
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
      writeResponse(id, await runNativeAgentTurn(id, payload));
      return;
    case "close_session":
      writeResponse(id, await closeAgentSessionRequest(payload));
      return;
    case "get_session_state":
      writeResponse(id, nativeSessionPayload(nativeSessionFromPayload(payload)));
      return;
    case "compact_session":
      writeResponse(id, await compactNativeAgentSession(id, payload));
      return;
    case "navigate_session":
      writeResponse(id, await navigateNativeAgentSession(id, payload));
      return;
    case "reload_session":
      writeResponse(id, await reloadNativeAgentSession(payload));
      return;
    case "export_session_jsonl":
      writeResponse(id, exportNativeAgentSession(payload));
      return;
    case "import_session_jsonl":
      writeResponse(id, await importNativeAgentSession(payload));
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
    case "list_discovered_skills":
      writeResponse(id, await listDiscoveredSkills(payload));
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
      writeResponse(id, await steerNativeAgentSession(payload));
      return;
    case "follow_up":
      writeResponse(id, await followUpNativeAgentSession(id, payload));
      return;
    case "host_tool_result":
      writeResponse(id, { accepted: resolveHostToolResult(payload) });
      return;
    case "host_tool_progress":
      writeResponse(id, { accepted: applyHostToolProgress(payload) });
      return;
    case "runtime_op_chunk":
      writeResponse(id, { accepted: runtimeOperationChunk(payload) });
      return;
    case "runtime_op_result":
      writeResponse(id, { accepted: runtimeOperationResult(payload) });
      return;
    case "runtime_op_cancel": {
      const operationId = asString(payload.operation_id).trim();
      const pending = pendingRuntimeOperations.get(operationId);
      if (pending) {
        pendingRuntimeOperations.delete(operationId);
        pending.reject(new Error(asString(payload.error, "Runtime operation cancelled by host.")));
      }
      writeResponse(id, { accepted: Boolean(pending) });
      return;
    }
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
    handleRequest(request).then(
      flushStartupCompileCache,
      (error) => {
        flushStartupCompileCache();
        writeError(request.id, error);
      },
    );
  }
}

main().catch((error) => {
  stderr.write(`pi-bridge fatal: ${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  process.exitCode = 1;
});
