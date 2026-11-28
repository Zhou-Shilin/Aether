import { stderr } from "node:process";

const SENSITIVE_KEY_FRAGMENTS = [
  "apikey",
  "api_key",
  "authorization",
  "authheader",
  "bearer",
  "token",
  "secret",
  "password",
  "credential",
  "cookie",
];
const LARGE_CONTENT_KEY_FRAGMENTS = [
  "base64",
  "screenshot",
  "attachment",
  "prompt",
  "content",
  "body",
  "markdown",
  "jsonl",
  "data",
];
const INLINE_SECRET_PATTERNS = [
  /(authorization\s*[:=]\s*bearer\s+)[^\s,;}{]+/gi,
  /((api[_-]?key|token|secret|password)\s*[:=]\s*)[^\s,;}{]+/gi,
  /((api[_-]?key|token|secret|password)=)[^&\s]+/gi,
];
const MAX_VALUE_CHARS = 700;

export function sanitizeUrl(rawUrl: string): string {
  const trimmed = rawUrl.trim();
  if (!trimmed) return "";
  try {
    const parsed = new URL(trimmed);
    const port = parsed.port ? `:${parsed.port}` : "";
    const pathname = parsed.pathname || "/";
    return `${parsed.protocol}//${parsed.hostname}${port}${pathname}`.replace(/\/+$/, "");
  } catch {
    return sanitizeString(trimmed);
  }
}

function normalizeKey(key: string): string {
  return key.replace(/-/g, "_").toLowerCase();
}

function sanitizeString(value: string): string {
  let sanitized = value;
  for (const pattern of INLINE_SECRET_PATTERNS) {
    sanitized = sanitized.replace(pattern, (_match, prefix: string) => `${prefix}[REDACTED]`);
  }
  if (sanitized.length <= MAX_VALUE_CHARS) return sanitized;
  return `${sanitized.slice(0, MAX_VALUE_CHARS - 14)}...[truncated]`;
}

function sanitizeValue(key: string, value: unknown, depth: number): unknown {
  if (value === null || value === undefined) return value ?? null;
  const normalized = normalizeKey(key);
  if (SENSITIVE_KEY_FRAGMENTS.some((fragment) => normalized.includes(fragment))) return "[REDACTED]";
  if (LARGE_CONTENT_KEY_FRAGMENTS.some((fragment) => normalized.includes(fragment))) {
    const text = typeof value === "string" ? value : safeStringify(value);
    return `[OMITTED chars=${text.length}]`;
  }
  if (typeof value === "string") return sanitizeString(value);
  if (typeof value === "number" || typeof value === "boolean") return value;
  if (value instanceof Error) {
    return sanitizeString(value.stack ?? value.message);
  }
  if (depth >= 4) return "[depth-limit]";
  if (Array.isArray(value)) {
    if (value.length > 32) return `[array length=${value.length}]`;
    return value.map((entry) => sanitizeValue("item", entry, depth + 1));
  }
  if (typeof value === "object") {
    const record = value as Record<string, unknown>;
    const result: Record<string, unknown> = {};
    for (const [entryKey, entryValue] of Object.entries(record)) {
      result[entryKey] = sanitizeValue(entryKey, entryValue, depth + 1);
    }
    return result;
  }
  return sanitizeString(String(value));
}

function safeStringify(value: unknown): string {
  try {
    return JSON.stringify(value) ?? "";
  } catch {
    return "[unserializable]";
  }
}

function normalizeDetails(details: Record<string, unknown>): string {
  const sanitized = sanitizeValue("", details, 0);
  return safeStringify(sanitized);
}

/**
 * Returns true when verbose bridge debug logging is enabled. Hosts can set
 * AETHER_PI_BRIDGE_DEBUG, and local troubleshooting can use PI_BRIDGE_DEBUG.
 */
export function bridgeDebugEnabled(): boolean {
  const raw = process.env.AETHER_PI_BRIDGE_DEBUG ?? process.env.PI_BRIDGE_DEBUG ?? "";
  const normalized = raw.trim().toLowerCase();
  return normalized !== "" && normalized !== "0" && normalized !== "false";
}

/**
 * Writes a single structured debug line to the bridge's stderr. The host
 * (PiKernelBridge) captures stderr into the app diagnostic log, so every line
 * emitted here shows up in the user-exportable diagnostics bundle.
 */
export function bridgeDebug(event: string, details: Record<string, unknown> = {}): void {
  if (!bridgeDebugEnabled()) return;
  writeBridgeDebug(event, details);
}

/**
 * Writes a small, non-sensitive diagnostic that must be available in exported
 * logs even when verbose bridge debugging is disabled.
 */
export function bridgeDiagnostic(event: string, details: Record<string, unknown> = {}): void {
  writeBridgeDebug(event, details);
}

function writeBridgeDebug(event: string, details: Record<string, unknown>): void {
  try {
    stderr.write(`[pi-bridge] ${event} ${normalizeDetails(details)}\n`);
  } catch {
    // Debug logging must never break the bridge.
  }
}

export function bridgeDebugError(event: string, error: unknown, details: Record<string, unknown> = {}): void {
  const message = error instanceof Error ? error.stack ?? error.message : String(error);
  bridgeDebug(event, { ...details, error: message });
}

export function elapsedMillis(startedAt: number): number {
  return Date.now() - startedAt;
}
