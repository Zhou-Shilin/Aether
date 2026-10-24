import * as fs from "node:fs";
import * as os from "node:os";
import * as path from "node:path";
import * as piAgentCore from "@earendil-works/pi-agent-core";
import {
  InMemoryCredentialStore,
  InMemoryModelsStore,
} from "@earendil-works/pi-ai";
import * as piAiCompat from "@earendil-works/pi-ai/compat";
import * as piAiOauth from "@earendil-works/pi-ai/oauth";
import * as piCodingAgent from "@earendil-works/pi-coding-agent";
import {
  createEventBus,
  createExtensionRuntime,
  DefaultPackageManager,
  ExtensionRunner,
  ModelRegistry,
  ModelRuntime,
  SessionManager,
  SettingsManager,
  wrapRegisteredTools,
  type Extension,
  type ExtensionFactory,
  type ExtensionRuntime,
} from "@earendil-works/pi-coding-agent";
import * as piTui from "@earendil-works/pi-tui";
import { createJiti } from "jiti/static";
import * as typebox from "typebox";
import * as typeboxCompile from "typebox/compile";
import * as typeboxValue from "typebox/value";
import { loadExtensionFromFactory } from "../node_modules/@earendil-works/pi-coding-agent/dist/core/extensions/loader.js";
import {
  aetherAppExtensionCountForManifest,
  aetherExtensionApiModule,
} from "./aether-extensions.js";
import {
  ensureExtensionPackageDependencies,
  packageRootForExtensionPath,
} from "./extension-dependencies.js";

const EXTENSION_FILE_PATTERN = /\.(?:[cm]?[jt]s)$/i;
const INDEX_FILE_NAMES = [
  "index.ts",
  "index.js",
  "index.mts",
  "index.mjs",
  "index.cts",
  "index.cjs",
];
const PI_AGENT_DIRECTORY = path.join(os.homedir(), ".pi", "agent");
const AETHER_JITI_CACHE = path.join(os.homedir(), ".aether", "cache", "jiti");

const virtualModules: Record<string, unknown> = {
  typebox,
  "typebox/compile": typeboxCompile,
  "typebox/value": typeboxValue,
  "@sinclair/typebox": typebox,
  "@sinclair/typebox/compile": typeboxCompile,
  "@sinclair/typebox/value": typeboxValue,
  "@earendil-works/pi-agent-core": piAgentCore,
  "@earendil-works/pi-ai": piAiCompat,
  "@earendil-works/pi-ai/compat": piAiCompat,
  "@earendil-works/pi-ai/oauth": piAiOauth,
  "@earendil-works/pi-coding-agent": piCodingAgent,
  "@earendil-works/pi-tui": piTui,
  "@mariozechner/pi-agent-core": piAgentCore,
  "@mariozechner/pi-ai": piAiCompat,
  "@mariozechner/pi-ai/compat": piAiCompat,
  "@mariozechner/pi-ai/oauth": piAiOauth,
  "@mariozechner/pi-coding-agent": piCodingAgent,
  "@mariozechner/pi-tui": piTui,
  "@baimoqilin/aether-extension-api": aetherExtensionApiModule,
  "@aether/extension-api": aetherExtensionApiModule,
  "@aether/android-extension": aetherExtensionApiModule,
};

export interface AetherExtensionLoadError {
  path: string;
  error: string;
}

export interface AetherExtensionRuntime {
  runner: ExtensionRunner;
  runtime: ExtensionRuntime;
  sessionManager: SessionManager;
  modelRegistry: ModelRegistry;
  paths: string[];
  errors: AetherExtensionLoadError[];
}

export interface AetherExtensionLoadOptions {
  disabledExtensionPaths?: string[];
  disabledPackageSources?: string[];
}

export {
  listExtensionPackages as listAetherExtensionPackages,
  installExtensionPackage as installAetherExtensionPackage,
  updateExtensionPackage as updateAetherExtensionPackage,
  removeExtensionPackage as removeAetherExtensionPackage,
} from "./extension-packages.js";
export type { InstalledExtensionPackage as AetherInstalledExtensionPackage } from "./extension-packages.js";

function readJsonFile(filePath: string): Record<string, unknown> | undefined {
  try {
    const parsed = JSON.parse(fs.readFileSync(filePath, "utf8"));
    return parsed && typeof parsed === "object" && !Array.isArray(parsed)
      ? (parsed as Record<string, unknown>)
      : undefined;
  } catch {
    return undefined;
  }
}

function isPathDisabled(
  candidatePath: string,
  disabledPaths: Set<string>,
): boolean {
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

function packageExtensionPaths(directory: string): string[] {
  const manifest = readJsonFile(path.join(directory, "package.json"));
  const pi = manifest?.pi;
  if (pi && typeof pi === "object" && !Array.isArray(pi)) {
    const configured = (pi as Record<string, unknown>).extensions;
    if (Array.isArray(configured)) {
      const paths = configured
        .filter((entry): entry is string => typeof entry === "string" && entry.trim().length > 0)
        .map((entry) => path.resolve(directory, entry));
      if (paths.length > 0) return paths;
    }
  }
  if (aetherAppExtensionCountForManifest(manifest) > 0) return [];
  for (const name of INDEX_FILE_NAMES) {
    const candidate = path.join(directory, name);
    if (fs.existsSync(candidate)) return [candidate];
  }
  return [];
}

function extensionEntriesAt(candidatePath: string): string[] {
  let stat: fs.Stats;
  try {
    stat = fs.statSync(candidatePath);
  } catch {
    return [];
  }
  if (stat.isFile()) {
    return EXTENSION_FILE_PATTERN.test(candidatePath) ? [path.resolve(candidatePath)] : [];
  }
  if (!stat.isDirectory()) return [];
  return packageExtensionPaths(candidatePath);
}

function extensionEntriesInRoot(root: string): string[] {
  let entries: fs.Dirent[];
  try {
    entries = fs.readdirSync(root, { withFileTypes: true });
  } catch {
    return [];
  }
  return entries
    .sort((left, right) => left.name.localeCompare(right.name))
    .flatMap((entry) => extensionEntriesAt(path.join(root, entry.name)));
}

export function discoverAetherExtensionPaths(
  cwd: string,
  configuredPaths: string[] = [],
): string[] {
  const roots = [
    path.join(os.homedir(), ".pi", "agent", "extensions"),
    path.join(cwd, ".pi", "extensions"),
    path.join(os.homedir(), ".aether", "extensions"),
  ];
  const discovered = [
    ...roots.flatMap(extensionEntriesInRoot),
    ...configuredPaths.flatMap((configuredPath) =>
      extensionEntriesAt(path.resolve(cwd, configuredPath)),
    ),
  ];
  return [...new Set(discovered.map((entry) => path.resolve(entry)))];
}

function createPackageManager(cwd: string): DefaultPackageManager {
  const settingsManager = SettingsManager.create(cwd, PI_AGENT_DIRECTORY, {
    projectTrusted: false,
  });
  return new DefaultPackageManager({
    cwd,
    agentDir: PI_AGENT_DIRECTORY,
    settingsManager,
  });
}

export async function discoverPackageExtensionPaths(
  cwd: string,
  disabledPackageSources: Set<string> = new Set(),
): Promise<string[]> {
  const resolved = await createPackageManager(cwd).resolve();
  return resolved.extensions
    .filter((entry) => entry.enabled)
    .filter((entry) => !disabledPackageSources.has(entry.metadata.source))
    .map((entry) => path.resolve(entry.path));
}

async function loadFactory(extensionPath: string): Promise<ExtensionFactory> {
  const jiti = createJiti(import.meta.url, {
    moduleCache: false,
    fsCache: AETHER_JITI_CACHE,
    tryNative: false,
    virtualModules,
  });
  const factory = await jiti.import<unknown>(extensionPath, { default: true });
  if (typeof factory !== "function") {
    throw new Error("Extension does not export a default factory function.");
  }
  return factory as ExtensionFactory;
}

export async function loadAetherExtensions(
  cwd: string,
  configuredPaths: string[] = [],
  loadOptions: AetherExtensionLoadOptions = {},
): Promise<AetherExtensionRuntime> {
  const disabledExtensionPaths = new Set(
    (loadOptions.disabledExtensionPaths ?? []).map((entry) => path.resolve(entry)),
  );
  const disabledPackageSources = new Set(loadOptions.disabledPackageSources ?? []);
  const paths = [
    ...discoverAetherExtensionPaths(cwd, configuredPaths)
      .filter((entry) => !isPathDisabled(entry, disabledExtensionPaths)),
    ...(await discoverPackageExtensionPaths(cwd, disabledPackageSources)),
  ].filter((entry, index, entries) => entries.indexOf(entry) === index);
  const runtime = createExtensionRuntime();
  const eventBus = createEventBus();
  const extensions: Extension[] = [];
  const errors: AetherExtensionLoadError[] = [];
  const installedDependencyRoots = new Set<string>();

  for (const extensionPath of paths) {
    try {
      const packageRoot = packageRootForExtensionPath(
        extensionPath,
        path.join(os.homedir(), ".aether", "extensions"),
      );
      if (packageRoot && !installedDependencyRoots.has(packageRoot)) {
        installedDependencyRoots.add(packageRoot);
        await ensureExtensionPackageDependencies(packageRoot);
      }
      const factory = await loadFactory(extensionPath);
      extensions.push(
        await loadExtensionFromFactory(factory, cwd, eventBus, runtime, extensionPath),
      );
    } catch (error) {
      errors.push({
        path: extensionPath,
        error: error instanceof Error ? error.message : String(error),
      });
    }
  }

  const sessionManager = SessionManager.inMemory(cwd);
  const modelRuntime = await ModelRuntime.create({
    credentials: new InMemoryCredentialStore(),
    modelsPath: null,
    modelsStore: new InMemoryModelsStore(),
    allowModelNetwork: false,
  });
  const modelRegistry = new ModelRegistry(modelRuntime);
  // The extension-facing facade has no refresh options, so keep catalog reloads offline.
  modelRegistry.refresh = () => modelRuntime.refresh({ allowNetwork: false });
  const runner = new ExtensionRunner(
    extensions,
    runtime,
    cwd,
    sessionManager,
    modelRegistry,
  );
  runner.setUIContext(undefined, "print");
  return {
    runner,
    runtime,
    sessionManager,
    modelRegistry,
    paths,
    errors,
  };
}

export function extensionTools(extensionRuntime: AetherExtensionRuntime) {
  return wrapRegisteredTools(
    extensionRuntime.runner.getAllRegisteredTools(),
    extensionRuntime.runner,
  );
}
