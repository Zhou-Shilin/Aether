import * as fs from "node:fs";
import * as os from "node:os";
import * as path from "node:path";
import { randomUUID } from "node:crypto";
import { DefaultPackageManager } from "../node_modules/@earendil-works/pi-coding-agent/dist/core/package-manager.js";
import { FileSettingsStorage, SettingsManager } from "../node_modules/@earendil-works/pi-coding-agent/dist/core/settings-manager.js";
import { loadSkills } from "../node_modules/@earendil-works/pi-coding-agent/dist/core/skills.js";
import { aetherAppExtensionCountForManifest } from "./aether-extensions.js";

const PI_AGENT_DIRECTORY = path.join(os.homedir(), ".pi", "agent");

type ConfiguredPackage = ReturnType<DefaultPackageManager["listConfiguredPackages"]>[number];
type ResolvedPaths = Awaited<ReturnType<DefaultPackageManager["resolve"]>>;

interface PackageResources {
  extensions: string[];
  skills: string[];
  prompts: string[];
  themes: string[];
}

export interface InstalledExtensionPackage {
  source: string;
  scope: "user" | "project";
  filtered: boolean;
  installedPath?: string;
  name: string;
  version: string;
  description: string;
  extensionCount: number;
  aetherExtensionCount: number;
  nativeEntrypointCount: number;
  skillCount: number;
  promptCount: number;
  themeCount: number;
  skillPaths: string[];
}

function readJsonFile(filePath: string): Record<string, unknown> | undefined {
  try {
    const parsed = JSON.parse(fs.readFileSync(filePath, "utf8"));
    return parsed && typeof parsed === "object" && !Array.isArray(parsed)
      ? parsed as Record<string, unknown>
      : undefined;
  } catch {
    return undefined;
  }
}

function createPackageManager(
  cwd: string,
  agentDirectory: string = PI_AGENT_DIRECTORY,
  projectTrusted = false,
): DefaultPackageManager {
  const settingsManager = createPackageSettings(cwd, agentDirectory, projectTrusted);
  return new DefaultPackageManager({
    cwd,
    agentDir: agentDirectory,
    settingsManager,
  });
}

// Keep the SDK's interprocess lock, but replace truncating writes with an atomic
// rename. Killing the runtime during an install must not erase all package sources.
function createPackageSettings(cwd: string, agentDirectory: string, projectTrusted = false): SettingsManager {
  const storage = new FileSettingsStorage(cwd, agentDirectory);
  const manager = SettingsManager.fromStorage({
    withLock(scope, update) {
      storage.withLock(scope, (current) => {
        const next = update(current);
        if (next === undefined) return undefined;
        const target = scope === "global"
          ? path.join(agentDirectory, "settings.json")
          : path.join(cwd, ".pi", "settings.json");
        fs.mkdirSync(path.dirname(target), { recursive: true });
        const temporary = `${target}.${randomUUID()}.tmp`;
        try {
          const descriptor = fs.openSync(temporary, "wx", 0o600);
          try {
            fs.writeFileSync(descriptor, next, "utf8");
            fs.fsyncSync(descriptor);
          } finally {
            fs.closeSync(descriptor);
          }
          fs.renameSync(temporary, target);
        } finally {
          fs.rmSync(temporary, { force: true });
        }
        return undefined;
      });
    },
  }, { projectTrusted });
  const failure = manager.drainErrors()[0];
  if (failure) throw failure.error;
  return manager;
}

async function flushPackageSettings(manager: SettingsManager): Promise<void> {
  await manager.flush();
  const failure = manager.drainErrors()[0];
  if (failure) throw failure.error;
}

export async function listDiscoveredSkills(
  cwd: string,
  agentDirectory: string = PI_AGENT_DIRECTORY,
  additionalSkillPaths: string[] = [],
): Promise<Array<{
  name: string;
  description: string;
  filePath: string;
  baseDir: string;
  source: string;
  scope: string;
  origin: string;
}>> {
  const managedRoot = path.resolve(cwd, ".aether", "skills");
  const resolved = await createPackageManager(cwd, agentDirectory, true).resolve();
  const resources = resolved.skills.filter((resource) => {
    if (!resource.enabled || resource.metadata.origin === "package") return false;
    const relative = path.relative(managedRoot, path.resolve(resource.path));
    return relative !== "" && (relative.startsWith(`..${path.sep}`) || path.isAbsolute(relative));
  });
  const loaded = loadSkills({
    cwd,
    agentDir: agentDirectory,
    skillPaths: [...resources.map((resource) => resource.path), ...additionalSkillPaths],
    includeDefaults: false,
  });
  return loaded.skills.map((skill) => {
    const filePath = path.resolve(skill.filePath);
    const resource = resources.find((candidate) => {
      const resourcePath = path.resolve(candidate.path);
      if (resourcePath === filePath) return true;
      try {
        const relative = path.relative(resourcePath, filePath);
        return fs.statSync(resourcePath).isDirectory() && relative !== ".." &&
          !relative.startsWith(`..${path.sep}`) && !path.isAbsolute(relative);
      } catch {
        return false;
      }
    });
    return {
      name: skill.name,
      description: skill.description,
      filePath: skill.filePath,
      baseDir: skill.baseDir,
      source: resource?.metadata.source ?? skill.sourceInfo.source,
      scope: resource?.metadata.scope ?? skill.sourceInfo.scope,
      origin: resource?.metadata.origin ?? skill.sourceInfo.origin,
    };
  });
}

function packageManifest(configuredPackage: ConfiguredPackage): Record<string, unknown> | undefined {
  if (!configuredPackage.installedPath) return undefined;
  return readJsonFile(path.join(configuredPackage.installedPath, "package.json"));
}

function manifestExtensionCount(manifest: Record<string, unknown> | undefined): number {
  const pi = manifest?.pi;
  if (!pi || typeof pi !== "object" || Array.isArray(pi)) return 0;
  const extensions = (pi as Record<string, unknown>).extensions;
  return Array.isArray(extensions)
    ? extensions.filter((entry) => typeof entry === "string").length
    : 0;
}

function manifestNativeEntrypointCount(manifest: Record<string, unknown> | undefined): number {
  const aether = manifest?.aether;
  if (!aether || typeof aether !== "object" || Array.isArray(aether)) return 0;
  const native = (aether as Record<string, unknown>).native;
  if (!native || typeof native !== "object" || Array.isArray(native)) return 0;
  const nativeManifest = native as Record<string, unknown>;
  if (nativeManifest.enabled === false) return 0;
  const countConfigured = (configured: unknown): number => {
    if (Array.isArray(configured)) {
      return configured.filter((entry) =>
        typeof entry === "string" && entry.trim().length > 0
      ).length;
    }
    return typeof configured === "string" && configured.trim().length > 0 ? 1 : 0;
  };
  return countConfigured(nativeManifest.entrypoints) || countConfigured(nativeManifest.entrypoint);
}

function packageResourcesForSource(source: string, resolved: ResolvedPaths): PackageResources {
  const pathsFor = (entries: ResolvedPaths["extensions"]): string[] =>
    entries
      .filter((entry) => entry.enabled && entry.metadata.source === source)
      .map((entry) => path.resolve(entry.path));
  return {
    extensions: pathsFor(resolved.extensions),
    skills: pathsFor(resolved.skills),
    prompts: pathsFor(resolved.prompts),
    themes: pathsFor(resolved.themes),
  };
}

function installedPackagePayload(
  configuredPackage: ConfiguredPackage,
  resources: PackageResources,
): InstalledExtensionPackage {
  const manifest = packageManifest(configuredPackage);
  return {
    source: configuredPackage.source,
    scope: configuredPackage.scope,
    filtered: configuredPackage.filtered,
    installedPath: configuredPackage.installedPath,
    name: (typeof manifest?.name === "string" && manifest.name.trim()) ||
      configuredPackage.source.replace(/^npm:/, ""),
    version: typeof manifest?.version === "string" ? manifest.version : "",
    description: typeof manifest?.description === "string" ? manifest.description : "",
    extensionCount: resources.extensions.length || manifestExtensionCount(manifest),
    aetherExtensionCount: aetherAppExtensionCountForManifest(manifest),
    nativeEntrypointCount: manifestNativeEntrypointCount(manifest),
    skillCount: resources.skills.length,
    promptCount: resources.prompts.length,
    themeCount: resources.themes.length,
    skillPaths: resources.skills,
  };
}

export async function listExtensionPackages(cwd: string): Promise<InstalledExtensionPackage[]> {
  const packageManager = createPackageManager(cwd);
  const configuredPackages = packageManager
    .listConfiguredPackages()
    .filter((configuredPackage) => configuredPackage.scope === "user");
  if (configuredPackages.length === 0) return [];
  const settings = createPackageSettings(cwd, PI_AGENT_DIRECTORY).getGlobalSettings();
  const packages: InstalledExtensionPackage[] = [];
  for (const configuredPackage of configuredPackages) {
    let resources: PackageResources = { extensions: [], skills: [], prompts: [], themes: [] };
    try {
      // Listing is read-only: a missing or interrupted package must never trigger
      // an npm install or hide the other packages while the network is unavailable.
      const entry = settings.packages?.find((entry) =>
        (typeof entry === "string" ? entry : entry.source) === configuredPackage.source
      );
      const isolated = new DefaultPackageManager({
        cwd,
        agentDir: PI_AGENT_DIRECTORY,
        settingsManager: SettingsManager.inMemory({ ...settings, packages: entry ? [entry] : [] }),
      });
      resources = packageResourcesForSource(configuredPackage.source, await isolated.resolve(async () => "skip"));
    } catch (error) {
      process.stderr.write(`Unable to inspect ${configuredPackage.source}: ${String(error)}\n`);
    }
    packages.push(installedPackagePayload(configuredPackage, resources));
  }
  return packages
    .sort((left, right) => left.name.localeCompare(right.name));
}

function requireNpmPackageSource(source: string): string {
  const normalized = source.trim();
  if (!normalized.startsWith("npm:") || normalized.length <= 4 || /\s/.test(normalized)) {
    throw new Error("Pi packages must use an npm: source.");
  }
  return normalized;
}

export async function installExtensionPackage(
  cwd: string,
  source: string,
  onProgress: (phase: string) => void = () => {},
): Promise<void> {
  const settingsManager = createPackageSettings(cwd, PI_AGENT_DIRECTORY);
  const manager = new DefaultPackageManager({ cwd, agentDir: PI_AGENT_DIRECTORY, settingsManager });
  onProgress("installing");
  await manager.installAndPersist(requireNpmPackageSource(source));
  onProgress("saving");
  await flushPackageSettings(settingsManager);
}

export async function removeExtensionPackage(cwd: string, source: string): Promise<boolean> {
  const normalized = requireNpmPackageSource(source);
  const settingsManager = createPackageSettings(cwd, PI_AGENT_DIRECTORY);
  const packageManager = new DefaultPackageManager({
    cwd,
    agentDir: PI_AGENT_DIRECTORY,
    settingsManager,
  });
  const configuredPackage = packageManager.listConfiguredPackages().find((entry) =>
    entry.scope === "user" && entry.source === normalized
  );
  if (!configuredPackage) return false;

  const installedPath = configuredPackage.installedPath;
  if (installedPath) {
    const managedRoot = path.resolve(PI_AGENT_DIRECTORY, "npm", "node_modules");
    const resolvedPath = path.resolve(installedPath);
    const relative = path.relative(managedRoot, resolvedPath);
    if (
      relative === "" || relative === ".." || relative.startsWith(`..${path.sep}`) ||
      path.isAbsolute(relative)
    ) {
      throw new Error("Refusing to remove an extension package outside the managed npm directory.");
    }
    fs.rmSync(resolvedPath, { recursive: true, force: true });
  }

  const configured = settingsManager.getGlobalSettings().packages ?? [];
  settingsManager.setPackages(configured.filter((entry) => {
    const configuredSource = typeof entry === "string" ? entry : entry.source;
    return configuredSource !== normalized;
  }));
  await flushPackageSettings(settingsManager);
  return true;
}

export async function updateExtensionPackage(
  cwd: string,
  source: string,
  onProgress: (phase: string) => void = () => {},
): Promise<void> {
  onProgress("installing");
  await createPackageManager(cwd).update(requireNpmPackageSource(source));
}
