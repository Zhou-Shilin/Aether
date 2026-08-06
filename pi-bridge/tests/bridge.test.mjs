import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { mkdtemp, mkdir, rm, writeFile } from "node:fs/promises";
import { createServer } from "node:http";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { createInterface } from "node:readline";
import { afterEach, test } from "node:test";

const activeClients = new Set();

class BridgeClient {
  constructor(environment = {}) {
    this.child = spawn(process.execPath, ["dist/bridge.mjs"], {
      cwd: process.cwd(),
      stdio: ["pipe", "pipe", "pipe"],
      env: { ...process.env, ...environment },
    });
    this.pending = new Map();
    this.events = [];
    this.eventWaiters = [];
    this.stderr = "";
    createInterface({ input: this.child.stdout }).on("line", (line) => {
      let frame;
      try {
        frame = JSON.parse(line);
      } catch (error) {
        const message = error instanceof Error ? error.message : String(error);
        this.failHarness(
          new Error(
            `Pi bridge emitted non-JSON stdout: ${message}${this.stderr ? `\nstderr: ${this.stderr}` : ""}`,
          ),
        );
        return;
      }
      if (frame.type === "event") {
        this.events.push(frame);
        const remaining = [];
        for (const waiter of this.eventWaiters) {
          if (waiter.predicate(frame)) {
            clearTimeout(waiter.timeout);
            waiter.resolve(frame);
          } else {
            remaining.push(waiter);
          }
        }
        this.eventWaiters = remaining;
        return;
      }
      const pending = this.pending.get(frame.id);
      if (!pending) return;
      this.pending.delete(frame.id);
      clearTimeout(pending.timeout);
      if (frame.type === "error" || frame.ok === false) {
        pending.reject(new Error(frame.error?.message || "Pi bridge request failed."));
      } else {
        pending.resolve(frame.payload);
      }
    });
    this.child.stderr.setEncoding("utf8");
    this.child.stderr.on("data", (chunk) => {
      this.stderr += chunk;
    });
    activeClients.add(this);
  }

  failHarness(error) {
    for (const pending of this.pending.values()) {
      clearTimeout(pending.timeout);
      pending.reject(error);
    }
    this.pending.clear();
    for (const waiter of this.eventWaiters) {
      clearTimeout(waiter.timeout);
      waiter.reject(error);
    }
    this.eventWaiters = [];
  }

  request(id, type, payload = {}, timeoutMs = 10_000) {
    this.child.stdin.write(`${JSON.stringify({ id, type, payload })}\n`);
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error(`Timed out waiting for ${type}: ${this.stderr}`));
      }, timeoutMs);
      this.pending.set(id, { resolve, reject, timeout });
    });
  }

  send(id, type, payload = {}) {
    this.child.stdin.write(`${JSON.stringify({ id, type, payload })}\n`);
  }

  waitForEvent(predicate, timeoutMs = 5_000) {
    const existing = this.events.find(predicate);
    if (existing) return Promise.resolve(existing);
    return new Promise((resolve, reject) => {
      const waiter = {
        predicate,
        resolve,
        reject,
        timeout: setTimeout(() => {
          this.eventWaiters = this.eventWaiters.filter((candidate) => candidate !== waiter);
          reject(new Error(`Timed out waiting for Pi event: ${this.stderr}`));
        }, timeoutMs),
      };
      this.eventWaiters.push(waiter);
    });
  }

  async close() {
    activeClients.delete(this);
    for (const pending of this.pending.values()) {
      clearTimeout(pending.timeout);
      pending.reject(new Error("Pi bridge test client closed."));
    }
    this.pending.clear();
    this.child.stdin.end();
    if (!this.child.killed) this.child.kill();
  }
}

afterEach(async () => {
  await Promise.all([...activeClients].map((client) => client.close()));
});

test("lists Pi extension packages from an isolated agent directory", async () => {
  const home = await mkdtemp(join(tmpdir(), "aether-pi-packages-"));
  const client = new BridgeClient({ HOME: home, USERPROFILE: home });
  try {
    const result = await client.request("packages-list", "list_extension_packages");
    assert.deepEqual(result.packages, []);
    await assert.rejects(
      client.request("packages-invalid", "install_extension_package", {
        source: "https://example.com/extension.zip",
      }),
      /npm: source/,
    );
  } finally {
    await client.close();
    await rm(home, { recursive: true, force: true });
  }
});

test("reports Native Mod entrypoints for installed npm packages", async () => {
  const home = await mkdtemp(join(tmpdir(), "aether-native-package-"));
  const agentDirectory = join(home, ".pi", "agent");
  const packageDirectory = join(
    agentDirectory,
    "npm",
    "node_modules",
    "aether-native-test",
  );
  await mkdir(packageDirectory, { recursive: true });
  await writeFile(
    join(agentDirectory, "settings.json"),
    JSON.stringify({ packages: ["npm:aether-native-test"] }),
    "utf8",
  );
  await writeFile(
    join(packageDirectory, "package.json"),
    JSON.stringify({
      name: "aether-native-test",
      version: "1.0.0",
      aether: {
        native: {
          classpath: ["./mod.dex"],
          entrypoints: [],
          entrypoint: "example.FallbackMod",
        },
      },
    }),
    "utf8",
  );

  const client = new BridgeClient({
    HOME: home,
    USERPROFILE: home,
    PI_OFFLINE: "1",
  });
  try {
    const result = await client.request(
      "native-packages-list",
      "list_extension_packages",
    );
    assert.equal(result.packages.length, 1);
    assert.equal(result.packages[0].source, "npm:aether-native-test");
    assert.equal(result.packages[0].native_entrypoint_count, 1);
  } finally {
    await client.close();
    await rm(home, { recursive: true, force: true });
  }
});

test("loads Aether UI extensions, renders native trees, runs actions, and intercepts events", async () => {
  const home = await mkdtemp(join(tmpdir(), "aether-app-extensions-"));
  const extensionDirectory = join(home, ".aether", "extensions", "demo");
  await mkdir(extensionDirectory, { recursive: true });
  await writeFile(
    join(extensionDirectory, "package.json"),
    JSON.stringify({
      name: "aether-demo",
      version: "1.0.0",
      aether: { extensions: ["./index.ts"] },
    }),
    "utf8",
  );
  await writeFile(
    join(extensionDirectory, "index.ts"),
    `
import { defineAetherExtension, ui } from "@aether/extension-api";

export default defineAetherExtension((aether) => {
  aether.registerAction("increment", async () => {
    const count = aether.storage.get("count", 0) + 1;
    aether.storage.set("count", count);
    await aether.host.invoke("app.notify", { message: "count=" + count });
    return { count };
  });
  aether.registerSurface("chat.composer.top", {
    id: "counter",
    render: ({ storage, draft_input }) =>
      ui.card([
        ui.text("Count " + (storage.count ?? 0)),
        ui.text("Draft " + (draft_input ?? "")),
        ui.button("Increment", "increment"),
      ]),
  });
  aether.registerComponent("chat.composer.actionTray", {
    id: "tray-wrapper",
    mode: "wrap",
    render: () => ui.card([
      ui.text("Wrapped tray"),
      ui.core(),
    ]),
  });
  aether.registerPage({
    id: "demo",
    title: "Demo",
    icon: "code",
    render: () => ui.text("Aether page"),
  });
  aether.registerAction("list-skills", async () =>
    aether.services.invoke("skills", "list"));
  aether.on("before_send", ({ text }) => ({ text: "[ext] " + text }));
  aether.intercept("chat.new", ({ selected_skill_ids }) => ({
    selected_skill_ids,
    intercepted: true,
  }));
});
`,
    "utf8",
  );

  const client = new BridgeClient({ HOME: home, USERPROFILE: home });
  try {
    const loaded = await client.request("aether-load", "reload_aether_extensions", {
      context: { draft_input: "hello" },
    });
    assert.equal(loaded.snapshot.extensions.length, 1);
    assert.equal(loaded.snapshot.surfaces[0].slot, "chat.composer.top");
    assert.equal(loaded.snapshot.surfaces[0].tree.children[0].text, "Count 0");
    assert.equal(loaded.snapshot.components[0].target, "chat.composer.actionTray");
    assert.equal(loaded.snapshot.components[0].mode, "wrap");
    assert.equal(loaded.snapshot.components[0].tree.children[1].type, "core");
    assert.equal(loaded.snapshot.pages[0].title, "Demo");
    assert.deepEqual(loaded.snapshot.event_names, ["before_send", "operation:chat.new"]);

    const disabled = await client.request("aether-disabled", "reload_aether_extensions", {
      disabled_extension_paths: [extensionDirectory],
      context: { draft_input: "hello" },
    });
    assert.deepEqual(disabled.snapshot.extensions, []);
    assert.deepEqual(disabled.snapshot.surfaces, []);
    assert.deepEqual(disabled.snapshot.pages, []);

    await client.request("aether-reenabled", "reload_aether_extensions", {
      disabled_extension_paths: [],
      disabled_package_sources: [],
      context: { draft_input: "hello" },
    });

    const actionPromise = client.request("aether-action", "invoke_aether_extension_action", {
      extension_id: loaded.snapshot.extensions[0].id,
      action: "increment",
      args: {},
      context: { draft_input: "hello" },
    });
    const hostCall = await client.waitForEvent(
      (frame) => frame.id === "aether-action" && frame.event === "aether_host_call",
    );
    assert.equal(hostCall.payload.method, "app.notify");
    await client.request("aether-host-result", "aether_host_result", {
      call_id: hostCall.payload.call_id,
      ok: true,
      result: { notified: true },
    });
    const actionResult = await actionPromise;
    assert.equal(actionResult.result.count, 1);
    assert.equal(actionResult.snapshot.surfaces[0].tree.children[0].text, "Count 1");

    const servicePromise = client.request("aether-service-action", "invoke_aether_extension_action", {
      extension_id: loaded.snapshot.extensions[0].id,
      action: "list-skills",
      args: {},
      context: {},
    });
    const serviceCall = await client.waitForEvent(
      (frame) => frame.id === "aether-service-action" && frame.event === "aether_host_call",
    );
    assert.equal(serviceCall.payload.method, "service.invoke");
    assert.equal(serviceCall.payload.args.service, "skills");
    assert.equal(serviceCall.payload.args.method, "list");
    await client.request("aether-service-host-result", "aether_host_result", {
      call_id: serviceCall.payload.call_id,
      ok: true,
      result: { skills: [] },
    });
    const serviceResult = await servicePromise;
    assert.deepEqual(serviceResult.result, { skills: [] });

    const eventResult = await client.request("aether-event", "dispatch_aether_extension_event", {
      event: "before_send",
      data: { text: "ship it" },
      context: {},
    });
    assert.equal(eventResult.handled, true);
    assert.equal(eventResult.cancelled, false);
    assert.equal(eventResult.payload.text, "[ext] ship it");
  } finally {
    await client.close();
    await rm(home, { recursive: true, force: true });
  }
});

test("keeps the last working Aether runtime when a reload factory fails", async () => {
  const home = await mkdtemp(join(tmpdir(), "aether-atomic-reload-"));
  const extensionDirectory = join(home, ".aether", "extensions", "atomic");
  const extensionPath = join(extensionDirectory, "index.ts");
  await mkdir(extensionDirectory, { recursive: true });
  await writeFile(
    join(extensionDirectory, "package.json"),
    JSON.stringify({
      name: "aether-atomic",
      version: "1.0.0",
      aether: { extensions: ["./index.ts"] },
    }),
    "utf8",
  );
  await writeFile(
    extensionPath,
    `
import { defineAetherExtension, ui } from "@baimoqilin/aether-extension-api";

export default defineAetherExtension((aether) => {
  aether.registerSurface("chat.composer.top", {
    id: "stable",
    render: () => ui.text("stable runtime"),
  });
  aether.registerAction("version", () => ({ version: "stable" }));
});
`,
    "utf8",
  );

  const client = new BridgeClient({ HOME: home, USERPROFILE: home });
  try {
    const loaded = await client.request(
      "atomic-load",
      "reload_aether_extensions",
    );
    const extensionId = loaded.snapshot.extensions[0].id;
    assert.equal(loaded.reloaded, true);
    assert.equal(loaded.snapshot.surfaces[0].tree.text, "stable runtime");

    await writeFile(
      extensionPath,
      `
import { defineAetherExtension, ui } from "@aether/extension-api";

export default defineAetherExtension((aether) => {
  aether.registerSurface("chat.composer.top", {
    id: "partial",
    render: () => ui.text("partial runtime"),
  });
  throw new Error("candidate failed after registration");
});
`,
      "utf8",
    );

    const rejected = await client.request(
      "atomic-reload-failed",
      "reload_aether_extensions",
    );
    assert.equal(rejected.reloaded, false);
    assert.match(rejected.errors[0].error, /candidate failed after registration/);
    assert.equal(rejected.snapshot.extensions[0].id, extensionId);
    assert.deepEqual(
      rejected.snapshot.surfaces.map((surface) => surface.id),
      [`${extensionId}:stable`],
    );
    assert.equal(rejected.snapshot.surfaces[0].tree.text, "stable runtime");

    const action = await client.request(
      "atomic-old-action",
      "invoke_aether_extension_action",
      {
        extension_id: extensionId,
        action: "version",
        args: {},
      },
    );
    assert.deepEqual(action.result, { version: "stable" });
  } finally {
    await client.close();
    await rm(home, { recursive: true, force: true });
  }
});

test("enforces Aether Script API compatibility ranges", async () => {
  const home = await mkdtemp(join(tmpdir(), "aether-api-range-"));
  const extensionDirectory = join(home, ".aether", "extensions", "api-range");
  const manifestPath = join(extensionDirectory, "package.json");
  await mkdir(extensionDirectory, { recursive: true });
  await writeFile(
    join(extensionDirectory, "index.ts"),
    `
import { defineAetherExtension } from "@aether/extension-api";
export default defineAetherExtension(() => {});
`,
    "utf8",
  );
  await writeFile(
    manifestPath,
    JSON.stringify({
      name: "aether-api-range",
      version: "1.0.0",
      aether: {
        api: { min: 3 },
        extensions: ["./index.ts"],
      },
    }),
    "utf8",
  );

  const client = new BridgeClient({ HOME: home, USERPROFILE: home });
  try {
    const incompatible = await client.request(
      "api-range-incompatible",
      "reload_aether_extensions",
    );
    assert.equal(incompatible.reloaded, false);
    assert.match(incompatible.errors[0].error, /API 3 or newer/);

    await writeFile(
      manifestPath,
      JSON.stringify({
        name: "aether-api-range",
        version: "1.0.0",
        aether: {
          api: { max: 1, allowNewer: true },
          extensions: ["./index.ts"],
        },
      }),
      "utf8",
    );
    const allowed = await client.request(
      "api-range-allow-newer",
      "reload_aether_extensions",
    );
    assert.equal(allowed.reloaded, true);
    assert.equal(allowed.snapshot.extensions.length, 1);
  } finally {
    await client.close();
    await rm(home, { recursive: true, force: true });
  }
});

test("reload_all_extensions honors Aether extension load filters", async () => {
  const home = await mkdtemp(join(tmpdir(), "aether-reload-all-filters-"));
  const extensionDirectory = join(home, ".aether", "extensions", "filtered");
  await mkdir(extensionDirectory, { recursive: true });
  await writeFile(
    join(extensionDirectory, "package.json"),
    JSON.stringify({
      name: "aether-filtered",
      version: "1.0.0",
      aether: { extensions: ["./index.ts"] },
    }),
    "utf8",
  );
  await writeFile(
    join(extensionDirectory, "index.ts"),
    `
import { defineAetherExtension } from "@aether/extension-api";
export default defineAetherExtension(() => {});
`,
    "utf8",
  );

  const client = new BridgeClient({ HOME: home, USERPROFILE: home });
  try {
    const disabled = await client.request(
      "reload-all-disabled",
      "reload_all_extensions",
      { disabled_extension_paths: [extensionDirectory] },
    );
    assert.equal(disabled.succeeded, true);
    assert.deepEqual(disabled.aether.extensions, []);

    const enabled = await client.request(
      "reload-all-enabled",
      "reload_all_extensions",
      {
        disabled_extension_paths: [],
        disabled_package_sources: [],
      },
    );
    assert.equal(enabled.succeeded, true);
    assert.equal(enabled.aether.extensions.length, 1);
  } finally {
    await client.close();
    await rm(home, { recursive: true, force: true });
  }
});

test("routes delayed Aether host calls through the persistent subscriber", async () => {
  const home = await mkdtemp(join(tmpdir(), "aether-background-host-"));
  const extensionDirectory = join(home, ".aether", "extensions", "background");
  await mkdir(extensionDirectory, { recursive: true });
  await writeFile(
    join(extensionDirectory, "package.json"),
    JSON.stringify({
      name: "aether-background",
      version: "1.0.0",
      aether: { extensions: ["./index.ts"] },
    }),
    "utf8",
  );
  await writeFile(
    join(extensionDirectory, "index.ts"),
    `
import { defineAetherExtension } from "@aether/extension-api";

export default defineAetherExtension((aether) => {
  aether.registerAction("schedule", () => {
    setTimeout(() => {
      void aether.host.invoke("app.notify", { message: "background" });
    }, 250);
    return { scheduled: true };
  });
});
`,
    "utf8",
  );

  const client = new BridgeClient({ HOME: home, USERPROFILE: home });
  try {
    const loaded = await client.request(
      "background-load",
      "reload_aether_extensions",
    );
    client.send("background-subscriber", "subscribe_aether_extensions");
    await client.waitForEvent(
      (frame) =>
        frame.id === "background-subscriber" &&
        frame.event === "aether_invalidated" &&
        frame.payload.subscribed === true,
    );

    const action = await client.request(
      "background-action",
      "invoke_aether_extension_action",
      {
        extension_id: loaded.snapshot.extensions[0].id,
        action: "schedule",
        args: {},
      },
    );
    assert.deepEqual(action.result, { scheduled: true });

    const hostCall = await client.waitForEvent(
      (frame) =>
        frame.id === "background-subscriber" &&
        frame.event === "aether_host_call" &&
        frame.payload.method === "app.notify",
    );
    assert.notEqual(hostCall.id, "background-action");
    await client.request("background-host-result", "aether_host_result", {
      call_id: hostCall.payload.call_id,
      ok: true,
      result: { notified: true },
    });
  } finally {
    await client.close();
    await rm(home, { recursive: true, force: true });
  }
});

function fauxConfig(overrides = {}) {
  return {
    provider_type: "faux",
    provider_config_id: "faux",
    pi_provider_id: "faux",
    pi_api: "faux",
    model_id: "faux-1",
    base_url: "http://localhost:0",
    reasoning: false,
    faux_response: "done",
    ...overrides,
  };
}

function turnPayload(sessionId, messages, config = fauxConfig(), hostTools = []) {
  return {
    session_id: sessionId,
    model_config: config,
    system_prompt: "Use the supplied tools when needed.",
    workspace_directory: process.cwd(),
    messages,
    host_tools: hostTools,
    reasoning: "off",
  };
}

function userMessage(text) {
  return { role: "user", content: [{ type: "text", text }] };
}

function hostTool(name, executionMode = "parallel") {
  return {
    name,
    description: `Run ${name}.`,
    parameters: {
      type: "object",
      properties: {
        value: { type: "string" },
        optional_note: { type: "string" },
      },
      required: ["value"],
      additionalProperties: false,
    },
    execution_mode: executionMode,
  };
}

async function respondToHostTool(client, frame, id) {
  return client.request(id, "host_tool_result", {
    session_id: frame.payload.session_id,
    tool_request_id: frame.payload.tool_request_id,
    tool_call_id: frame.payload.tool_call_id,
    tool_name: frame.payload.tool_name,
    arguments_json: frame.payload.arguments_json,
    output_json: JSON.stringify({ ok: true, stdout: frame.payload.tool_name }),
    raw_output_json: JSON.stringify({ ok: true, stdout: frame.payload.tool_name }),
    is_error: false,
    content: [{ type: "text", text: JSON.stringify({ ok: true }) }],
  });
}

test("reports pinned bridge and Pi versions", async () => {
  const client = new BridgeClient();
  const ping = await client.request("ping-1", "ping");

  assert.equal(ping.bridge_version, "2.0.0-alpha.0");
  assert.equal(ping.pi_ai_version, "0.83.0");
  assert.equal(ping.pi_agent_core_version, "0.83.0");
  assert.equal(ping.pi_coding_agent_version, "0.83.0");
  assert.match(ping.node_version, /^v\d+\./);
});

test("loads source-compatible Pi TypeScript extensions and runs their tools", async (t) => {
  const workspace = await mkdtemp(join(tmpdir(), "aether-pi-extension-"));
  t.after(() => rm(workspace, { recursive: true, force: true }));
  const extensionDirectory = join(workspace, ".pi", "extensions");
  await mkdir(extensionDirectory, { recursive: true });
  await writeFile(
    join(extensionDirectory, "echo.ts"),
    `
import type { ExtensionAPI } from "@mariozechner/pi-coding-agent";
import { Type } from "typebox";

export default function (pi: ExtensionAPI) {
  let started = false;
  pi.on("session_start", () => { started = true; });
  pi.registerTool({
    name: "extension_echo",
    label: "Extension Echo",
    description: "Echo text through a Pi extension.",
    parameters: Type.Object({ text: Type.String() }),
    async execute(_id, params) {
      return {
        content: [{ type: "text", text: \`extension:\${params.text}:started=\${started}\` }],
        details: {},
      };
    },
  });
}
`,
    "utf8",
  );

  const client = new BridgeClient();
  const run = client.request(
    "extension-turn",
    "run_turn",
    {
      ...turnPayload(
        "session-extension",
        [userMessage("use the extension")],
        fauxConfig({
          faux_response: "extension finished",
          faux_tool_calls: [
            { id: "extension-call", name: "extension_echo", arguments: { text: "hello" } },
          ],
        }),
      ),
      workspace_directory: workspace,
    },
  );
  const toolEnd = await client.waitForEvent(
    (frame) =>
      frame.id === "extension-turn" &&
      frame.event === "tool_call_end" &&
      frame.payload.name === "extension_echo",
  );
  assert.equal(toolEnd.payload.output_json, "extension:hello:started=true");
  assert.equal((await run).assistant_text, "extension finished");

  const listed = await client.request("extension-list", "list_extensions", {
    session_id: "session-extension",
  });
  assert.equal(listed.custom_tui_supported, false);
  assert.ok(listed.extension_paths.some((extensionPath) => extensionPath.endsWith("echo.ts")));
  assert.ok(listed.tools.some((tool) => tool.name === "extension_echo"));
});

test("reloads Pi extensions atomically for an existing harness session", async (t) => {
  const workspace = await mkdtemp(join(tmpdir(), "aether-pi-reload-"));
  t.after(() => rm(workspace, { recursive: true, force: true }));
  const extensionDirectory = join(workspace, ".pi", "extensions");
  const extensionPath = join(extensionDirectory, "reloadable.ts");
  await mkdir(extensionDirectory, { recursive: true });

  const source = (version) => `
import type { ExtensionAPI } from "@earendil-works/pi-coding-agent";
import { Type } from "typebox";
export default function (pi: ExtensionAPI) {
  pi.registerCommand("extension-version", {
    description: "Active extension version: ${version}",
    async handler() {},
  });
  pi.registerTool({
    name: "reloadable_echo",
    label: "Reloadable Echo",
    description: "Return the active extension version.",
    parameters: Type.Object({}),
    async execute() {
      return { content: [{ type: "text", text: "${version}" }], details: {} };
    },
  });
}
`;
  await writeFile(extensionPath, source("v1"), "utf8");

  const client = new BridgeClient();
  await client.request(
    "reload-create",
    "run_turn",
    {
      ...turnPayload("session-reload", [userMessage("start")]),
      workspace_directory: workspace,
    },
  );
  await writeFile(extensionPath, source("v2"), "utf8");
  const reload = await client.request("reload-request", "reload_extensions", {
    session_id: "session-reload",
  });
  assert.equal(reload.reloaded, true);
  assert.equal(reload.scheduled, false);
  assert.deepEqual(reload.errors, []);
  assert.ok(
    reload.commands.some(
      (command) =>
        command.name === "extension-version" &&
        command.description === "Active extension version: v2",
    ),
  );
  const invoked = await client.request("reload-command", "invoke_extension_command", {
    session_id: "session-reload",
    command: "/extension-version",
  });
  assert.equal(invoked.invoked, true);
});

test("runs text turns and reuses the persisted Pi assistant session", async () => {
  const client = new BridgeClient();
  const config = fauxConfig({ faux_response: "first answer" });
  const first = await client.request(
    "turn-1",
    "run_turn",
    turnPayload("session-persist", [userMessage("hello")], config),
  );
  assert.equal(first.assistant_text, "first answer");
  assert.equal(first.session_reused, false);

  const second = await client.request(
    "turn-2",
    "run_turn",
    turnPayload(
      "session-persist",
      [
        userMessage("hello"),
        {
          role: "assistant",
          content: [{ type: "text", text: first.assistant_text }],
          provider_payload: {
            piAssistantMessage: first.assistant_message,
            provider: first.provider,
            model: first.model,
          },
        },
        userMessage("continue"),
      ],
      config,
    ),
  );
  assert.equal(second.session_reused, true);
  assert.equal(second.assistant_text, "first answer");
});

test("rebuilds a harness when any earlier persisted history changes", async () => {
  const client = new BridgeClient();
  const config = fauxConfig({ faux_response: "history answer" });
  const first = await client.request(
    "history-1",
    "run_turn",
    turnPayload("session-history-signature", [userMessage("ORIGINAL HISTORY")], config),
  );

  const second = await client.request(
    "history-2",
    "run_turn",
    turnPayload(
      "session-history-signature",
      [
        userMessage("REPLACED HISTORY"),
        {
          role: "assistant",
          content: [{ type: "text", text: first.assistant_text }],
          provider_payload: {
            piAssistantMessage: first.assistant_message,
            provider: first.provider,
            model: first.model,
          },
        },
        userMessage("continue"),
      ],
      config,
    ),
  );

  assert.equal(second.session_reused, false);
});

test("closes harness sessions explicitly", async () => {
  const client = new BridgeClient();
  await client.request(
    "close-create",
    "run_turn",
    turnPayload("session-close", [userMessage("hello")]),
  );

  const closed = await client.request("close-session", "close_session", {
    session_id: "session-close",
  });
  assert.equal(closed.closed, true);
  await assert.rejects(
    client.request("close-follow-up", "follow_up", {
      session_id: "session-close",
      message: userMessage("still there?"),
    }),
    /Unknown Pi session/,
  );
});

test("evicts least-recently-used idle harness sessions", async () => {
  const client = new BridgeClient({ AETHER_PI_MAX_HARNESS_SESSIONS: "2" });
  await client.request("lru-a", "run_turn", turnPayload("session-lru-a", [userMessage("a")]));
  await new Promise((resolve) => setTimeout(resolve, 5));
  await client.request("lru-b", "run_turn", turnPayload("session-lru-b", [userMessage("b")]));
  await new Promise((resolve) => setTimeout(resolve, 5));
  await client.request("lru-c", "run_turn", turnPayload("session-lru-c", [userMessage("c")]));

  await assert.rejects(
    client.request("lru-a-follow-up", "follow_up", {
      session_id: "session-lru-a",
      message: userMessage("again"),
    }),
    /Unknown Pi session/,
  );
});

test("expires idle harness sessions after the configured TTL", async () => {
  const client = new BridgeClient({ AETHER_PI_HARNESS_SESSION_TTL_MS: "20" });
  await client.request("ttl-a", "run_turn", turnPayload("session-ttl-a", [userMessage("a")]));
  await new Promise((resolve) => setTimeout(resolve, 40));
  await client.request("ttl-b", "run_turn", turnPayload("session-ttl-b", [userMessage("b")]));

  await assert.rejects(
    client.request("ttl-a-follow-up", "follow_up", {
      session_id: "session-ttl-a",
      message: userMessage("again"),
    }),
    /Unknown Pi session/,
  );
});

test("rebuilds a persisted harness when the host tool set changes", async () => {
  const client = new BridgeClient();
  const config = fauxConfig({ faux_response: "first answer" });
  const first = await client.request(
    "tool-signature-1",
    "run_turn",
    turnPayload("session-tool-signature", [userMessage("hello")], config),
  );

  const second = await client.request(
    "tool-signature-2",
    "run_turn",
    turnPayload(
      "session-tool-signature",
      [
        userMessage("hello"),
        {
          role: "assistant",
          content: [{ type: "text", text: first.assistant_text }],
          provider_payload: {
            piAssistantMessage: first.assistant_message,
            provider: first.provider,
            model: first.model,
          },
        },
        userMessage("continue with a new tool"),
      ],
      config,
      [hostTool("agent_display", "sequential")],
    ),
  );

  assert.equal(second.session_reused, false);
});

test("routes harness tool calls through the host and resumes with the result", async () => {
  const client = new BridgeClient();
  const config = fauxConfig({
    faux_response: "tool finished",
    faux_tool_calls: [{ id: "call-1", name: "read", arguments: { path: "README.md" } }],
  });
  const run = client.request(
    "tool-turn",
    "run_turn",
    turnPayload(
      "session-tool",
      [userMessage("read the file")],
      config,
      [
        {
          name: "read",
          description: "Read a file.",
          parameters: {
            type: "object",
            properties: { path: { type: "string" } },
            required: ["path"],
            additionalProperties: false,
          },
          execution_mode: "parallel",
        },
      ],
    ),
  );
  const hostRequest = await client.waitForEvent(
    (frame) => frame.id === "tool-turn" && frame.event === "host_tool_request",
  );
  assert.equal(hostRequest.payload.tool_name, "read");
  const hostResult = await client.request("host-result", "host_tool_result", {
    session_id: "session-tool",
    tool_request_id: hostRequest.payload.tool_request_id,
    tool_call_id: hostRequest.payload.tool_call_id,
    tool_name: "read",
    arguments_json: hostRequest.payload.arguments_json,
    output_json: JSON.stringify({ ok: true, stdout: "contents" }),
    raw_output_json: JSON.stringify({ ok: true, stdout: "contents" }),
    is_error: false,
    content: [{ type: "text", text: JSON.stringify({ ok: true, stdout: "contents" }) }],
  });
  assert.equal(hostResult.accepted, true);
  const result = await run;
  assert.equal(result.assistant_text, "tool finished");
  assert.ok(
    client.events.some(
      (frame) => frame.id === "tool-turn" && frame.event === "tool_call_end",
    ),
  );
});

test("runs parallel host tools concurrently", async () => {
  const client = new BridgeClient();
  const config = fauxConfig({
    faux_response: "parallel finished",
    faux_tool_calls: [
      { id: "parallel-a", name: "parallel_a", arguments: { value: "a" } },
      { id: "parallel-b", name: "parallel_b", arguments: { value: "b" } },
    ],
  });
  const run = client.request(
    "parallel-turn",
    "run_turn",
    turnPayload(
      "session-parallel",
      [userMessage("run both")],
      config,
      [hostTool("parallel_a"), hostTool("parallel_b")],
    ),
  );

  const [first, second] = await Promise.all([
    client.waitForEvent(
      (frame) =>
        frame.id === "parallel-turn" &&
        frame.event === "host_tool_request" &&
        frame.payload.tool_name === "parallel_a",
    ),
    client.waitForEvent(
      (frame) =>
        frame.id === "parallel-turn" &&
        frame.event === "host_tool_request" &&
        frame.payload.tool_name === "parallel_b",
    ),
  ]);
  assert.equal(first.payload.execution_mode, "parallel");
  assert.equal(second.payload.execution_mode, "parallel");
  await Promise.all([
    respondToHostTool(client, first, "parallel-result-a"),
    respondToHostTool(client, second, "parallel-result-b"),
  ]);

  const result = await run;
  assert.equal(result.assistant_text, "parallel finished");
});

test("runs sequential host tools one at a time", async () => {
  const client = new BridgeClient();
  const config = fauxConfig({
    faux_response: "sequential finished",
    faux_tool_calls: [
      { id: "sequential-a", name: "sequential_a", arguments: { value: "a" } },
      { id: "sequential-b", name: "sequential_b", arguments: { value: "b" } },
    ],
  });
  const run = client.request(
    "sequential-turn",
    "run_turn",
    turnPayload(
      "session-sequential",
      [userMessage("run in order")],
      config,
      [hostTool("sequential_a", "sequential"), hostTool("sequential_b", "sequential")],
    ),
  );

  const first = await client.waitForEvent(
    (frame) =>
      frame.id === "sequential-turn" &&
      frame.event === "host_tool_request" &&
      frame.payload.tool_name === "sequential_a",
  );
  assert.equal(first.payload.execution_mode, "sequential");
  await assert.rejects(
    client.waitForEvent(
      (frame) =>
        frame.id === "sequential-turn" &&
        frame.event === "host_tool_request" &&
        frame.payload.tool_name === "sequential_b",
      150,
    ),
    /Timed out waiting for Pi event/,
  );
  await respondToHostTool(client, first, "sequential-result-a");
  const second = await client.waitForEvent(
    (frame) =>
      frame.id === "sequential-turn" &&
      frame.event === "host_tool_request" &&
      frame.payload.tool_name === "sequential_b",
  );
  await respondToHostTool(client, second, "sequential-result-b");

  const result = await run;
  assert.equal(result.assistant_text, "sequential finished");
});

test("accepts steer and follow-up messages on a live persistent harness", async () => {
  const client = new BridgeClient();
  const config = fauxConfig({
    faux_response: "working response with enough tokens to keep the stream active briefly",
    faux_tokens_per_second: 12,
  });
  const run = client.request(
    "steer-turn",
    "run_turn",
    turnPayload("session-steer", [userMessage("start")], config),
    15_000,
  );

  let steer;
  for (let attempt = 0; attempt < 30; attempt += 1) {
    steer = await client.request(`steer-${attempt}`, "steer", {
      session_id: "session-steer",
      message: userMessage("include this too"),
    });
    if (steer.accepted) break;
    await new Promise((resolve) => setTimeout(resolve, 20));
  }
  assert.equal(steer?.accepted, true);
  const steeredResult = await run;
  assert.equal(steeredResult.assistant_text.includes("working response"), true);

  const followUp = await client.request(
    "follow-up",
    "follow_up",
    {
      session_id: "session-steer",
      message: userMessage("one more question"),
    },
    15_000,
  );
  assert.equal(followUp.assistant_text.includes("working response"), true);
});

test("aborts an active harness by session id", async () => {
  const client = new BridgeClient();
  const config = fauxConfig({
    faux_response: Array.from({ length: 80 }, () => "slow").join(" "),
    faux_tokens_per_second: 1,
  });
  const run = client.request(
    "abort-turn",
    "run_turn",
    turnPayload("session-abort", [userMessage("start slowly")], config),
    15_000,
  );

  let abortResult;
  for (let attempt = 0; attempt < 30; attempt += 1) {
    abortResult = await client.request(`abort-${attempt}`, "abort", {
      session_id: "session-abort",
    });
    if (abortResult.aborted) break;
    await new Promise((resolve) => setTimeout(resolve, 20));
  }
  assert.equal(abortResult?.aborted, true);
  void run.catch(() => {});
});

test("maps a custom OpenAI-compatible provider through Pi", async (t) => {
  let receivedRequest;
  const server = createServer((request, response) => {
    const chunks = [];
    request.on("data", (chunk) => chunks.push(chunk));
    request.on("end", () => {
      receivedRequest = {
        url: request.url,
        authorization: request.headers.authorization,
        customHeader: request.headers["x-aether-test"],
        body: JSON.parse(Buffer.concat(chunks).toString("utf8")),
      };
      response.writeHead(200, { "content-type": "text/event-stream" });
      response.write(
        `data: ${JSON.stringify({
          id: "chatcmpl-pi-test",
          object: "chat.completion.chunk",
          created: 1,
          model: "custom-model",
          choices: [
            {
              index: 0,
              delta: { role: "assistant", content: "CUSTOM_OK" },
              finish_reason: null,
            },
          ],
        })}\n\n`,
      );
      response.write(
        `data: ${JSON.stringify({
          id: "chatcmpl-pi-test",
          object: "chat.completion.chunk",
          created: 1,
          model: "custom-model",
          choices: [{ index: 0, delta: {}, finish_reason: "stop" }],
          usage: {
            prompt_tokens: 4,
            completion_tokens: 2,
            total_tokens: 6,
          },
        })}\n\n`,
      );
      response.end("data: [DONE]\n\n");
    });
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  t.after(() => new Promise((resolve) => server.close(resolve)));
  const address = server.address();
  assert.ok(address && typeof address === "object");

  const client = new BridgeClient();
  const result = await client.request(
    "custom-completion",
    "complete_once",
    {
      model_config: {
        provider_type: "openai_compatible",
        provider_config_id: "custom-completion",
        pi_provider_id: "aether-test",
        pi_api: "openai-completions",
        model_id: "custom-model",
        base_url: `http://127.0.0.1:${address.port}/v1`,
        api_key: "secret-key",
        custom_headers: { "X-Aether-Test": "present" },
        reasoning: false,
      },
      system_prompt: "Reply briefly.",
      messages: [userMessage("hello")],
      stream: false,
    },
  );

  assert.equal(result.assistant_text, "CUSTOM_OK", JSON.stringify(result));
  assert.equal(receivedRequest.url, "/v1/chat/completions");
  assert.equal(receivedRequest.authorization, "Bearer secret-key");
  assert.equal(receivedRequest.customHeader, "present");
  assert.equal(receivedRequest.body.model, "custom-model");
});

test("uses the OpenAI native protocol for custom base URL models outside the built-in catalog", async (t) => {
  let receivedRequest;
  const server = createServer((request, response) => {
    const chunks = [];
    request.on("data", (chunk) => chunks.push(chunk));
    request.on("end", () => {
      receivedRequest = {
        url: request.url,
        authorization: request.headers.authorization,
        customHeader: request.headers["x-aether-test"],
        body: JSON.parse(Buffer.concat(chunks).toString("utf8")),
      };
      response.writeHead(400, { "content-type": "application/json" });
      response.end(JSON.stringify({ error: { message: "expected test failure" } }));
    });
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  t.after(() => new Promise((resolve) => server.close(resolve)));
  const address = server.address();
  assert.ok(address && typeof address === "object");

  const client = new BridgeClient();
  const result = await client.request("custom-openai-native", "complete_once", {
    model_config: {
      provider_type: "builtin",
      provider_config_id: "custom-openai-native",
      pi_provider_id: "openai",
      pi_api: "builtin",
      model_id: "third-party-model",
      base_url: `http://127.0.0.1:${address.port}/v1`,
      api_key: "secret-key",
      custom_headers: { "X-Aether-Test": "present" },
      reasoning: false,
      max_retries: 0,
    },
    system_prompt: "Reply briefly.",
    messages: [userMessage("hello")],
    stream: false,
  });

  assert.match(result.error_message, /expected test failure|400/);
  assert.equal(receivedRequest.url, "/v1/responses");
  assert.equal(receivedRequest.authorization, "Bearer secret-key");
  assert.equal(receivedRequest.customHeader, "present");
  assert.equal(receivedRequest.body.model, "third-party-model");
});

test("rejects unknown built-in models when the provider uses its default base URL", async () => {
  const client = new BridgeClient();
  await assert.rejects(
    client.request("unknown-default-openai", "complete_once", {
      model_config: {
        provider_type: "builtin",
        provider_config_id: "unknown-default-openai",
        pi_provider_id: "openai",
        pi_api: "builtin",
        model_id: "third-party-model",
        base_url: "https://api.openai.com/v1",
        api_key: "secret-key",
      },
      messages: [userMessage("hello")],
      stream: false,
    }),
    /Unknown model third-party-model/,
  );
});

test("lists every built-in Pi provider and its model catalog", async () => {
  const client = new BridgeClient();
  const catalog = await client.request("providers", "list_providers");
  const providers = catalog.providers;

  assert.equal(providers.length, 35);
  assert.equal(new Set(providers.map((provider) => provider.id)).size, 35);
  assert.ok(providers.every((provider) => provider.models.length > 0));
  assert.ok(providers.every((provider) => provider.models.every((model) => model.id)));

  const oauthProviders = providers
    .filter((provider) => provider.auth.oauth)
    .map((provider) => provider.id)
    .sort();
  assert.deepEqual(oauthProviders, ["anthropic", "github-copilot", "openai-codex"]);
});

test("returns Pi thinking capabilities for a known model on a custom endpoint", async () => {
  const client = new BridgeClient();
  const capabilities = await client.request("custom-capabilities", "get_model_capabilities", {
    model_config: {
      provider_type: "custom",
      provider_config_id: "custom-kimi",
      pi_provider_id: "aether-modal",
      pi_api: "openai-completions",
      model_id: "kimi-k3",
      base_url: "https://example.modal.run/v1",
      reasoning: false,
    },
  });

  assert.equal(capabilities.reasoning, true);
  assert.deepEqual(capabilities.thinking_levels, ["low", "high", "max"]);
  assert.equal(capabilities.thinking_level_clamps.medium, "high");
});

test("validates Pi OAuth protocol requests without legacy provider fallbacks", async () => {
  const client = new BridgeClient();
  const promptResult = await client.request("prompt-missing", "auth_prompt_result", {
    prompt_id: "missing",
    value: "unused",
  });
  assert.equal(promptResult.accepted, false);

  await assert.rejects(
    client.request("oauth-unsupported", "login_provider", {
      provider_id: "openai",
      provider_config_id: `test-${"openai"}`,
    }),
    /does not support OAuth/,
  );
  await assert.rejects(
    client.request("oauth-unknown", "login_provider", {
      provider_id: "legacy-custom-provider",
      provider_config_id: "test-unknown",
    }),
    /Unknown built-in Pi provider/,
  );
});

test("bundles every Pi OAuth flow into the standalone bridge", async () => {
  const client = new BridgeClient();
  const providers = [
    ["openai-codex", "select"],
    ["github-copilot", "text"],
    ["anthropic", "manual_code"],
  ];

  for (const [providerId, expectedPromptType] of providers) {
    const requestId = `oauth-bundle-${providerId}`;
    const login = client.request(
      requestId,
      "login_provider",
      {
        provider_id: providerId,
        provider_config_id: `test-${providerId}`,
      },
      15_000,
    );
    const prompt = await client.waitForEvent(
      (frame) => frame.id === requestId && frame.event === "auth_prompt",
      10_000,
    );
    assert.equal(prompt.payload.prompt_type, expectedPromptType);

    const cancelled = await client.request(`cancel-${providerId}`, "auth_prompt_result", {
      prompt_id: prompt.payload.prompt_id,
      cancelled: true,
    });
    assert.equal(cancelled.accepted, true);
    await assert.rejects(login, /cancel/i);
  }
});

test("keeps Codex browser OAuth on the manual redirect flow", async () => {
  const client = new BridgeClient();
  const login = client.request(
    "oauth-codex-manual",
    "login_provider",
    {
      provider_id: "openai-codex",
      provider_config_id: "test-openai-codex",
      oauth_flow: "browser",
    },
    15_000,
  );

  const authUrl = await client.waitForEvent(
    (frame) => frame.id === "oauth-codex-manual" && frame.event === "auth_url",
  );
  const manualPrompt = await client.waitForEvent(
    (frame) =>
      frame.id === "oauth-codex-manual" &&
      frame.event === "auth_prompt" &&
      frame.payload.prompt_type === "manual_code",
  );
  assert.match(authUrl.payload.instructions, /copy the full URL back into Aether/i);
  assert.equal(manualPrompt.payload.placeholder, "http://localhost:...");

  const state = new URL(authUrl.payload.url).searchParams.get("state");
  await assert.rejects(
    fetch(`http://127.0.0.1:1455/auth/callback?code=test-code&state=${state}`, {
      signal: AbortSignal.timeout(500),
    }),
  );

  const cancelled = await client.request("oauth-codex-cancel", "auth_prompt_result", {
    prompt_id: manualPrompt.payload.prompt_id,
    cancelled: true,
  });
  assert.equal(cancelled.accepted, true);
  await assert.rejects(login, /cancel/i);
});

test("uses Pi provider-specific API key login prompts", async () => {
  const client = new BridgeClient();

  const openAILogin = client.request("api-key-openai", "login_provider", {
    provider_id: "openai",
    provider_config_id: `test-${"openai"}`,
    auth_method: "api_key",
  });
  const openAIPrompt = await client.waitForEvent(
    (frame) =>
      frame.id === "api-key-openai" &&
      frame.event === "auth_prompt" &&
      frame.payload.message === "Enter OpenAI API key",
  );
  await client.request("api-key-openai-result", "auth_prompt_result", {
    prompt_id: openAIPrompt.payload.prompt_id,
    value: "openai-test-key",
  });
  const openAIResult = await openAILogin;
  assert.equal(openAIResult.auth_method, "api_key");
  assert.equal(openAIResult.api_key, "openai-test-key");

  const cloudflareLogin = client.request("api-key-cloudflare", "login_provider", {
    provider_id: "cloudflare-ai-gateway",
    provider_config_id: `test-${"cloudflare-ai-gateway"}`,
    auth_method: "api_key",
  });
  const cloudflareAnswers = [
    ["Enter Cloudflare API key", "cloudflare-test-key"],
    ["Enter Cloudflare account ID", "account-id"],
    ["Enter Cloudflare AI Gateway ID", "gateway-id"],
  ];
  for (const [message, value] of cloudflareAnswers) {
    const prompt = await client.waitForEvent(
      (frame) =>
        frame.id === "api-key-cloudflare" &&
        frame.event === "auth_prompt" &&
        frame.payload.message === message,
    );
    await client.request(`cloudflare-${value}`, "auth_prompt_result", {
      prompt_id: prompt.payload.prompt_id,
      value,
    });
  }
  const cloudflareResult = await cloudflareLogin;
  assert.equal(cloudflareResult.api_key, "cloudflare-test-key");
  assert.deepEqual(cloudflareResult.provider_env, {
    CLOUDFLARE_ACCOUNT_ID: "account-id",
    CLOUDFLARE_GATEWAY_ID: "gateway-id",
  });

  await assert.rejects(
    client.request("api-key-bedrock", "login_provider", {
      provider_id: "amazon-bedrock",
      provider_config_id: `test-${"amazon-bedrock"}`,
      auth_method: "api_key",
    }),
    /ambient credentials/,
  );
});

test("rejects non-OpenAI custom Pi APIs", async () => {
  const client = new BridgeClient();
  for (const piApi of ["anthropic-messages", "google-vertex", "openai-responses"]) {
    await assert.rejects(
      client.request(`custom-${piApi}`, "complete_once", {
        model_config: {
          provider_type: "custom",
          provider_config_id: `custom-${piApi}`,
          pi_provider_id: "custom-test",
          pi_api: piApi,
          model_id: "custom-model",
          base_url: "http://127.0.0.1:1/v1",
        },
        messages: [userMessage("hello")],
      }),
      /Unsupported custom Pi API/,
    );
  }
});
