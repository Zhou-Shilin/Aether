import { spawn } from "node:child_process";
import { createHash } from "node:crypto";
import * as fs from "node:fs";
import * as path from "node:path";

const DEPENDENCY_FIELDS = [
  "dependencies",
  "optionalDependencies",
  "peerDependencies",
] as const;
const INSTALL_MARKER_NAME = ".aether-install-complete";
const INSTALL_TIMEOUT_MS = 5 * 60 * 1000;
const pendingInstalls = new Map<string, Promise<void>>();

function readManifest(packageRoot: string): Record<string, unknown> | undefined {
  try {
    const value = JSON.parse(
      fs.readFileSync(path.join(packageRoot, "package.json"), "utf8"),
    );
    return value && typeof value === "object" && !Array.isArray(value)
      ? value as Record<string, unknown>
      : undefined;
  } catch {
    return undefined;
  }
}

function hasRuntimeDependencies(manifest: Record<string, unknown>): boolean {
  return DEPENDENCY_FIELDS.some((field) => {
    const value = manifest[field];
    return value && typeof value === "object" && !Array.isArray(value) &&
      Object.keys(value).length > 0;
  });
}

function dependencyFingerprint(manifest: Record<string, unknown>): string {
  const dependencies = Object.fromEntries(
    DEPENDENCY_FIELDS.map((field) => [field, manifest[field] ?? null]),
  );
  return createHash("sha256")
    .update(JSON.stringify(dependencies))
    .digest("hex");
}

function isWithin(root: string, candidate: string): boolean {
  const relative = path.relative(path.resolve(root), path.resolve(candidate));
  return relative === "" || (
    relative !== ".." &&
    !relative.startsWith(`..${path.sep}`) &&
    !path.isAbsolute(relative)
  );
}

/** Find the package root for a bundled/imported entry without crossing the Aether root. */
export function packageRootForExtensionPath(
  extensionPath: string,
  extensionRoot: string,
): string | undefined {
  const root = path.resolve(extensionRoot);
  let candidate = path.resolve(extensionPath);
  try {
    if (!fs.statSync(candidate).isDirectory()) candidate = path.dirname(candidate);
  } catch {
    candidate = path.dirname(candidate);
  }
  while (isWithin(root, candidate)) {
    if (fs.existsSync(path.join(candidate, "package.json"))) return candidate;
    if (candidate === root) break;
    candidate = path.dirname(candidate);
  }
  return undefined;
}

function runNpmInstall(packageRoot: string): Promise<void> {
  const command = fs.existsSync(path.join(packageRoot, "package-lock.json"))
    ? "ci"
    : "install";
  const args = [
    command,
    "--omit=dev",
    "--omit=optional",
    "--legacy-peer-deps",
    "--no-audit",
    "--no-fund",
    "--prefer-offline",
  ];
  return new Promise((resolve, reject) => {
    const child = spawn("npm", args, {
      cwd: packageRoot,
      env: process.env,
      stdio: ["ignore", "pipe", "pipe"],
    });
    let stdout = "";
    let stderr = "";
    child.stdout?.on("data", (chunk: Buffer) => { stdout += chunk.toString(); });
    child.stderr?.on("data", (chunk: Buffer) => { stderr += chunk.toString(); });
    let settled = false;
    const finish = (callback: () => void) => {
      if (settled) return;
      settled = true;
      clearTimeout(timeout);
      callback();
    };
    const timeout = setTimeout(() => {
      child.kill("SIGTERM");
      finish(() => reject(new Error("Timed out installing extension npm dependencies.")));
    }, INSTALL_TIMEOUT_MS);
    child.once("error", (error) => finish(() => reject(error)));
    child.once("close", (code, signal) => finish(() => {
      if (code === 0) {
        resolve();
        return;
      }
      const details = (stderr || stdout).trim();
      reject(new Error(
        details || `npm ${command} exited with ${signal ?? `code ${code ?? "unknown"}`}.`,
      ));
    }));
  });
}

/** Install runtime dependencies once for a copied package. */
export async function ensureExtensionPackageDependencies(
  packageRoot: string,
  onProgress?: (phase: string) => void,
): Promise<boolean> {
  const resolvedRoot = path.resolve(packageRoot);
  const manifest = readManifest(resolvedRoot);
  if (!manifest || !hasRuntimeDependencies(manifest)) return false;

  const nodeModules = path.join(resolvedRoot, "node_modules");
  const marker = path.join(nodeModules, INSTALL_MARKER_NAME);
  const fingerprint = dependencyFingerprint(manifest);
  // A node_modules directory can be left behind by an interrupted npm run.
  // Only a marker for the current manifest proves the tree is complete.
  if (fs.existsSync(marker) && fs.readFileSync(marker, "utf8").trim() === fingerprint) return false;

  onProgress?.("installing");
  const activeInstall = pendingInstalls.get(resolvedRoot);
  if (activeInstall) {
    await activeInstall;
    return true;
  }
  const install = (async () => {
    await runNpmInstall(resolvedRoot);
    fs.mkdirSync(nodeModules, { recursive: true });
    fs.writeFileSync(marker, `${fingerprint}\n`, "utf8");
  })();
  pendingInstalls.set(resolvedRoot, install);
  try {
    await install;
    return true;
  } finally {
    if (pendingInstalls.get(resolvedRoot) === install) pendingInstalls.delete(resolvedRoot);
  }
}
