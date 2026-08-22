import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { copyFile, mkdtemp, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { createServer } from "node:http";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { createInterface } from "node:readline";
import { afterEach, test } from "node:test";
import {
  createBashToolDefinition,
  createEditToolDefinition,
  createFindToolDefinition,
  createGrepToolDefinition,
  createLsToolDefinition,
  createReadToolDefinition,
  createWriteToolDefinition,
} from "@earendil-works/pi-coding-agent";

const activeClients = new Set();

class BridgeClient {
  constructor(environment = {}, bridgePath = "dist/bridge.mjs", cwd = process.cwd()) {
    this.child = spawn(process.execPath, [bridgePath], {
      cwd,
      stdio: ["pipe", "pipe", "pipe"],
      env: { ...process.env, ...environment },
    });
    this.pending = new Map();
    this.events = [];
    this.eventWaiters = [];
    this.stderr = "";
    createInterface({ input: this.child.stdout }).on("line", (line) => {
      if (!line.trim()) return;
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

test("lists Pi-discovered project skills without Aether managed copies", async () => {
  const root = await mkdtemp(join(tmpdir(), "aether-skills-"));
  const workspace = join(root, "workspace");
  const agentDir = join(root, "agent");
  const projectSkill = join(workspace, ".agents", "skills", "review");
  const managedSkill = join(workspace, ".aether", "skills", "managed");
  await mkdir(projectSkill, { recursive: true });
  await mkdir(managedSkill, { recursive: true });
  await mkdir(agentDir, { recursive: true });
  await writeFile(
    join(projectSkill, "SKILL.md"),
    "---\nname: review\ndescription: Reviews code changes\n---\n",
  );
  await writeFile(
    join(managedSkill, "SKILL.md"),
    "---\nname: managed\ndescription: Already managed by Aether\n---\n",
  );
  const client = new BridgeClient({ HOME: root });
  try {
    const payload = await client.request("skills-1", "list_discovered_skills", {
      workspace_directory: workspace,
      agent_directory: agentDir,
      workspace_trusted: true,
    });
    assert.deepEqual(payload.skills.map((skill) => skill.name), ["review"]);
    assert.equal(payload.skills[0].file_path, join(projectSkill, "SKILL.md"));
  } finally {
    await client.close();
    await rm(root, { recursive: true, force: true });
  }
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

test("removes extension packages without reloading their code", async () => {
  const home = await mkdtemp(join(tmpdir(), "aether-remove-package-"));
  const agentDirectory = join(home, ".pi", "agent");
  const packageDirectory = join(
    agentDirectory,
    "npm",
    "node_modules",
    "aether-remove-test",
  );
  await mkdir(packageDirectory, { recursive: true });
  await writeFile(
    join(agentDirectory, "settings.json"),
    JSON.stringify({ packages: ["npm:aether-remove-test"] }),
    "utf8",
  );
  await writeFile(
    join(packageDirectory, "package.json"),
    JSON.stringify({
      name: "aether-remove-test",
      version: "1.0.0",
      aether: { extensions: ["./broken.ts"] },
    }),
    "utf8",
  );
  await writeFile(
    join(packageDirectory, "broken.ts"),
    "export default (aether) => aether.registerSettings({ id: 'old', title: 'Old', sections: [] });\n",
    "utf8",
  );

  const client = new BridgeClient({
    HOME: home,
    USERPROFILE: home,
    PI_OFFLINE: "1",
  });
  try {
    const result = await client.request("package-remove", "remove_extension_package", {
      source: "npm:aether-remove-test",
    });
    assert.equal(result.removed, true);
    assert.equal(Object.hasOwn(result, "reload"), false);
    assert.deepEqual(result.packages, []);
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

let removeFetchToolTitle = () => {};

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
  aether.registerAction("settings:preferences:enabled", ({ value }) => {
    aether.storage.set("settings:preferences:enabled", value === true);
    return { customSettingsAction: true };
  });
  aether.registerSettings({
    id: "preferences",
    title: "Preferences",
    sections: [{
      title: "General",
      settings: [{ id: "enabled", label: "Enabled", type: "toggle", default: true }],
    }],
  });
  aether.registerSettings({
    id: "secondary",
    title: "Secondary",
    order: 1,
    sections: [{
      settings: [{ id: "enabled", label: "Enabled", type: "toggle", default: true }],
    }],
  });
  aether.registerComposerMenuItem({ id: "run", title: "Run demo", action: "run" });
  aether.registerMessageType({ type: "demo", render: ({ message }) => ui.text(String(message.text ?? "")) });
  aether.registerToolTitle("web_search", "Searching the web", "Searched the web", 200);
  removeFetchToolTitle = aether.registerToolTitle("fetch_content", "Fetching content", "Fetched content");
  aether.registerAction("remove-fetch-title", () => {
    removeFetchToolTitle();
    return { removed: true };
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
    assert.equal(loaded.snapshot.pages, undefined);
    assert.equal(loaded.snapshot.settings[0].title, "Preferences");
    assert.equal(loaded.snapshot.settings[0].sections[0].settings[0].id, "enabled");
    assert.equal(loaded.snapshot.settings[1].title, "Secondary");
    assert.equal(loaded.snapshot.composer_menu_items[0].title, "Run demo");
    assert.equal(loaded.snapshot.message_types[0].type, "demo");
    assert.deepEqual(
      loaded.snapshot.tool_titles.map(({ tool_name, running_title, completed_title, priority }) => ({
        tool_name,
        running_title,
        completed_title,
        priority,
      })),
      [
        { tool_name: "fetch_content", running_title: "Fetching content", completed_title: "Fetched content", priority: 100 },
        { tool_name: "web_search", running_title: "Searching the web", completed_title: "Searched the web", priority: 200 },
      ],
    );
    assert.deepEqual(loaded.snapshot.event_names, ["before_send", "operation:chat.new"]);

    const settingsResult = await client.request(
      "aether-settings-action",
      "invoke_aether_extension_action",
      {
        extension_id: loaded.snapshot.extensions[0].id,
        action: "settings:preferences:enabled",
        args: { value: false },
        context: {},
      },
    );
    assert.equal(settingsResult.result.customSettingsAction, true);
    assert.equal(settingsResult.snapshot.settings[0].sections[0].settings[0].value, false);
    assert.equal(settingsResult.snapshot.settings[1].sections[0].settings[0].value, true);

    const removedTitle = await client.request(
      "aether-remove-title-action",
      "invoke_aether_extension_action",
      {
        extension_id: loaded.snapshot.extensions[0].id,
        action: "remove-fetch-title",
        args: {},
        context: {},
      },
    );
    assert.equal(removedTitle.result.removed, true);
    assert.deepEqual(
      removedTitle.snapshot.tool_titles.map(({ tool_name }) => tool_name),
      ["web_search"],
    );

    const disabled = await client.request("aether-disabled", "reload_aether_extensions", {
      disabled_extension_paths: [extensionDirectory],
      context: { draft_input: "hello" },
    });
    assert.deepEqual(disabled.snapshot.extensions, []);
    assert.deepEqual(disabled.snapshot.surfaces, []);
    assert.deepEqual(disabled.snapshot.tool_titles, []);

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

test("installs dependencies for bundled Aether packages before loading them", async () => {
  const home = await mkdtemp(join(tmpdir(), "aether-preinstalled-deps-"));
  const packageDirectory = join(home, ".aether", "extensions", "dependency-test");
  const binDirectory = join(home, "bin");
  const npmLog = join(home, "npm.log");
  await mkdir(packageDirectory, { recursive: true });
  await mkdir(binDirectory, { recursive: true });
  await writeFile(
    join(packageDirectory, "package.json"),
    JSON.stringify({
      name: "dependency-test",
      version: "1.0.0",
      dependencies: { "fixture-dependency": "1.0.0" },
      aether: { extensions: ["./index.ts"] },
    }),
    "utf8",
  );
  await writeFile(
    join(packageDirectory, "index.ts"),
    `export default (aether) => aether.registerAction("loaded", () => ({ loaded: true }));\n`,
    "utf8",
  );
  // Simulate an interrupted install. A bare node_modules directory must not be
  // mistaken for a complete dependency tree.
  await mkdir(join(packageDirectory, "node_modules"), { recursive: true });
  await writeFile(
    join(binDirectory, "npm"),
    `#!/bin/sh\nprintf '%s\\n' "$*" >> "$AETHER_TEST_NPM_LOG"\nmkdir -p node_modules\n`,
    { encoding: "utf8", mode: 0o755 },
  );

  const client = new BridgeClient({
    HOME: home,
    USERPROFILE: home,
    PATH: `${binDirectory}:${process.env.PATH ?? ""}`,
    AETHER_TEST_NPM_LOG: npmLog,
  });
  try {
    const first = await client.request("dependency-load", "reload_aether_extensions");
    assert.equal(first.reloaded, true);
    assert.equal(first.snapshot.extensions.length, 1);
    assert.match(await readFile(npmLog, "utf8"), /^install /m);
    assert.ok(await readFile(join(packageDirectory, "node_modules", ".aether-install-complete"), "utf8"));

    await client.request("dependency-reload", "reload_aether_extensions");
    assert.equal((await readFile(npmLog, "utf8")).trim().split("\n").length, 1);

    const manifestPath = join(packageDirectory, "package.json");
    const updatedManifest = JSON.parse(await readFile(manifestPath, "utf8"));
    updatedManifest.dependencies["second-fixture-dependency"] = "2.0.0";
    await writeFile(manifestPath, JSON.stringify(updatedManifest), "utf8");
    await client.request("dependency-update", "reload_aether_extensions");
    assert.equal((await readFile(npmLog, "utf8")).trim().split("\n").length, 2);
  } finally {
    await client.close();
    await rm(home, { recursive: true, force: true });
  }
});

test("does not let one broken Aether extension block unrelated reload operations", async () => {
  const home = await mkdtemp(join(tmpdir(), "aether-broken-extension-"));
  const root = join(home, ".aether", "extensions");
  const broken = join(root, "broken");
  const healthy = join(root, "healthy");
  await mkdir(broken, { recursive: true });
  await writeFile(
    join(broken, "package.json"),
    JSON.stringify({ name: "broken", aether: { extensions: ["./index.ts"] } }),
    "utf8",
  );
  await writeFile(join(broken, "index.ts"), `export default () => { throw new Error("broken extension"); };\n`, "utf8");

  const client = new BridgeClient({ HOME: home, USERPROFILE: home });
  try {
    const blocked = await client.request("broken-only", "reload_all_extensions");
    assert.equal(blocked.succeeded, true);
    assert.equal(blocked.aether_reload.reloaded, false);
    assert.match(blocked.aether_reload.errors[0].error, /broken extension/);

    await mkdir(healthy, { recursive: true });
    await writeFile(
      join(healthy, "package.json"),
      JSON.stringify({ name: "healthy", aether: { extensions: ["./index.ts"] } }),
      "utf8",
    );
    await writeFile(
      join(healthy, "index.ts"),
      `import { ui } from "@aether/extension-api"; export default (aether) => aether.registerSurface("chat.composer.top", ui.text("healthy"));\n`,
      "utf8",
    );

    const recovered = await client.request("broken-with-healthy", "reload_all_extensions");
    assert.equal(recovered.succeeded, true);
    assert.equal(recovered.aether_reload.reloaded, true);
    assert.deepEqual(recovered.aether.extensions.map(({ name }) => name), ["healthy"]);
    assert.match(recovered.aether_reload.errors[0].error, /broken extension/);
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

const RESPONSES_TEST_TURN_COUNT = 3;
const RESPONSES_TEST_PROMPT = "x".repeat(60_000);

function openAIResponseEvents(responseNumber) {
  const text = `answer-${responseNumber}`;
  const messageId = `msg_${responseNumber}`;
  return [
    {
      type: "response.output_item.added",
      item: {
        type: "message",
        id: messageId,
        role: "assistant",
        status: "in_progress",
        content: [],
      },
    },
    { type: "response.content_part.added", part: { type: "output_text", text: "" } },
    { type: "response.output_text.delta", delta: text },
    {
      type: "response.output_item.done",
      item: {
        type: "message",
        id: messageId,
        role: "assistant",
        status: "completed",
        content: [{ type: "output_text", text }],
      },
    },
    {
      type: "response.completed",
      response: {
        id: `resp_${responseNumber}`,
        status: "completed",
        usage: {
          input_tokens: 5,
          output_tokens: 3,
          input_tokens_details: { cached_tokens: 0 },
          output_tokens_details: { reasoning_tokens: 0 },
        },
      },
    },
  ];
}

function openAIEncryptedReasoningToolEvents() {
  const reasoningItem = {
    type: "reasoning",
    id: "rs_gateway_1",
    status: "completed",
    summary: [{ type: "summary_text", text: "route-specific reasoning" }],
    encrypted_content: "gAAAA-route-specific-encrypted-content",
  };
  const functionCall = {
    type: "function_call",
    id: "fc_gateway_1",
    call_id: "call_gateway_1",
    name: "aether_config_get",
    arguments: JSON.stringify({ value: "general" }),
    status: "completed",
  };
  return [
    {
      type: "response.output_item.added",
      output_index: 0,
      item: { ...reasoningItem, status: "in_progress", summary: [], encrypted_content: null },
    },
    { type: "response.output_item.done", output_index: 0, item: reasoningItem },
    {
      type: "response.output_item.added",
      output_index: 1,
      item: { ...functionCall, status: "in_progress", arguments: "" },
    },
    { type: "response.output_item.done", output_index: 1, item: functionCall },
    {
      type: "response.completed",
      response: {
        id: "resp_gateway_1",
        status: "completed",
        output: [reasoningItem, functionCall],
        usage: {
          input_tokens: 5,
          output_tokens: 3,
          total_tokens: 8,
          input_tokens_details: { cached_tokens: 0 },
          output_tokens_details: { reasoning_tokens: 2 },
        },
      },
    },
  ];
}

async function createOpenAIResponsesServer() {
  const requests = [];
  let responseCount = 0;
  const server = createServer((request, response) => {
    const chunks = [];
    request.on("data", (chunk) => chunks.push(chunk));
    request.on("end", () => {
      const body = JSON.parse(Buffer.concat(chunks).toString("utf8"));
      requests.push({ url: request.url, body });
      responseCount += 1;
      response.writeHead(200, { "content-type": "text/event-stream" });
      for (const event of openAIResponseEvents(responseCount)) {
        response.write(`data: ${JSON.stringify(event)}\n\n`);
      }
      response.end("data: [DONE]\n\n");
    });
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const address = server.address();
  assert.ok(address && typeof address === "object");
  return {
    requests,
    baseUrl: `http://127.0.0.1:${address.port}/v1`,
    close: () => new Promise((resolve) => server.close(resolve)),
  };
}

function openAIFetchRedirectEnvironment(targetBaseUrl) {
  const preloadSource = `
const targetOrigin = process.env.AETHER_TEST_OPENAI_REDIRECT_ORIGIN;
if (!targetOrigin) throw new Error("AETHER_TEST_OPENAI_REDIRECT_ORIGIN is required.");
const originalFetch = globalThis.fetch;
globalThis.fetch = (input, init) => {
  const rawUrl = typeof input === "string" || input instanceof URL ? String(input) : input.url;
  const url = new URL(rawUrl);
  if (url.hostname !== "api.openai.com") return originalFetch(input, init);
  return originalFetch(new URL(url.pathname + url.search, targetOrigin), init);
};
`;
  const preloadSpecifier = `data:text/javascript,${encodeURIComponent(preloadSource)}`;
  return {
    AETHER_TEST_OPENAI_REDIRECT_ORIGIN: new URL(targetBaseUrl).origin,
    NODE_OPTIONS: `${process.env.NODE_OPTIONS ?? ""} --import=${preloadSpecifier}`.trim(),
  };
}

function openAIResponsesModelConfig(baseUrl, overrides = {}) {
  return {
    provider_type: "builtin",
    provider_config_id: "responses-test",
    pi_provider_id: "openai",
    pi_api: "builtin",
    model_id: "gpt-5.6-sol",
    base_url: baseUrl,
    api_key: "secret-key",
    reasoning: true,
    context_window: 128_000,
    max_tokens: 16_384,
    max_retries: 0,
    ...overrides,
  };
}

async function runResponsesTurns(client, sessionId, modelConfig, sessionDirectory) {
  for (let turn = 1; turn <= RESPONSES_TEST_TURN_COUNT; turn += 1) {
    await client.request(
      `${sessionId}-turn-${turn}`,
      "run_turn",
      {
        ...turnPayload(
          sessionId,
          [userMessage(`turn ${turn}: ${RESPONSES_TEST_PROMPT}`)],
          modelConfig,
        ),
        workspace_directory: sessionDirectory,
        session_directory: sessionDirectory,
        max_retries: 0,
      },
      20_000,
    );
  }
}

test("reports pinned bridge and Pi versions", async () => {
  const client = new BridgeClient();
  const ping = await client.request("ping-1", "ping");

  assert.equal(ping.bridge_version, "2.0.0-alpha.0");
  assert.equal(ping.pi_ai_version, "0.84.1");
  assert.equal(ping.pi_agent_core_version, "0.84.1");
  assert.equal(ping.pi_coding_agent_version, "0.84.1");
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

test("loads TypeBox extensions from the standalone bridge bundle", async (t) => {
  const root = await mkdtemp(join(tmpdir(), "aether-standalone-bridge-"));
  t.after(() => rm(root, { recursive: true, force: true }));
  const bridgePath = join(root, "bridge.mjs");
  const workspace = join(root, "workspace");
  const extensionDirectory = join(workspace, ".pi", "extensions");
  await mkdir(extensionDirectory, { recursive: true });
  await copyFile(join(process.cwd(), "dist", "bridge.mjs"), bridgePath);
  await writeFile(
    join(extensionDirectory, "standalone.ts"),
    `
import { Type } from "typebox";
export default (pi) => pi.registerTool({
  name: "standalone_tool",
  label: "Standalone Tool",
  description: "Loaded without a neighboring node_modules directory.",
  parameters: Type.Object({}),
  async execute() { return { content: [{ type: "text", text: "ok" }], details: {} }; },
});
`,
    "utf8",
  );

  const client = new BridgeClient({}, bridgePath, root);
  try {
    await client.request("standalone-turn", "run_turn", {
      ...turnPayload("session-standalone", [userMessage("start")]),
      workspace_directory: workspace,
    });
    const listed = await client.request("standalone-list", "list_extensions", {
      session_id: "session-standalone",
    });
    assert.deepEqual(listed.errors, []);
    assert.ok(listed.tools.some((tool) => tool.name === "standalone_tool"));
  } finally {
    await client.close();
  }
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

test("recreates existing sessions after disabling a Pi extension", async (t) => {
  const workspace = await mkdtemp(join(tmpdir(), "aether-pi-disable-"));
  t.after(() => rm(workspace, { recursive: true, force: true }));
  const extensionDirectory = join(workspace, ".pi", "extensions");
  await mkdir(extensionDirectory, { recursive: true });
  await writeFile(
    join(extensionDirectory, "disable-me.ts"),
    `
export default function (pi) {
  pi.registerCommand("disable-me", { description: "Disable me", async handler() {} });
}
`,
    "utf8",
  );

  const client = new BridgeClient();
  await client.request(
    "disable-create",
    "run_turn",
    {
      ...turnPayload("session-disable", [userMessage("start")]),
      workspace_directory: workspace,
    },
  );
  const before = await client.request("disable-list-before", "list_extensions", {
    session_id: "session-disable",
  });
  assert.ok(before.commands.some((command) => command.name === "disable-me"));

  const reload = await client.request("disable-reload", "reload_all_extensions", {
    disabled_extension_paths: [extensionDirectory],
    disabled_package_sources: [],
  });
  assert.equal(reload.sessions[0].recreated_on_next_use, true);

  await client.request(
    "disable-recreate",
    "run_turn",
    {
      ...turnPayload("session-disable", [userMessage("continue")]),
      workspace_directory: workspace,
      disabled_extension_paths: [extensionDirectory],
      disabled_package_sources: [],
    },
  );
  const after = await client.request("disable-list-after", "list_extensions", {
    session_id: "session-disable",
  });
  assert.ok(after.commands.every((command) => command.name !== "disable-me"));
});

test("recreates a reused session when a new Pi extension becomes discoverable", async (t) => {
  const workspace = await mkdtemp(join(tmpdir(), "aether-pi-extension-discovery-"));
  t.after(() => rm(workspace, { recursive: true, force: true }));
  const extensionDirectory = join(workspace, ".pi", "extensions");
  await mkdir(extensionDirectory, { recursive: true });

  const client = new BridgeClient();
  try {
    await client.request("discovery-before", "run_turn", {
      ...turnPayload("session-discovery", [userMessage("start")]),
      workspace_directory: workspace,
    });
    await writeFile(
      join(extensionDirectory, "discovered.ts"),
      `
import { Type } from "typebox";
export default (pi) => pi.registerTool({
  name: "discovered_tool",
  label: "Discovered Tool",
  description: "A tool added after the session was created.",
  parameters: Type.Object({}),
  async execute() { return { content: [{ type: "text", text: "ok" }], details: {} }; },
});
`,
      "utf8",
    );
    const rerun = await client.request("discovery-after", "run_turn", {
      ...turnPayload("session-discovery", [userMessage("refresh")]),
      workspace_directory: workspace,
    });
    assert.equal(rerun.session_reused, false);
    const listed = await client.request("discovery-list", "list_extensions", {
      session_id: "session-discovery",
    });
    assert.ok(listed.tools.some((tool) => tool.name === "discovered_tool"));
  } finally {
    await client.close();
  }
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

test("closes AgentSession instances explicitly", async () => {
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

test("rehydrates a persisted AgentSession before navigation", async (t) => {
  const home = await mkdtemp(join(tmpdir(), "aether-session-rehydrate-"));
  t.after(() => rm(home, { recursive: true, force: true }));
  const client = new BridgeClient({ HOME: home, USERPROFILE: home });
  const config = fauxConfig();
  const first = await client.request(
    "rehydrate-create",
    "run_turn",
    turnPayload("session-rehydrate", [userMessage("hello")], config),
  );
  await client.request("rehydrate-close", "close_session", {
    session_id: "session-rehydrate",
  });

  const navigation = await client.request("rehydrate-navigate", "navigate_session", {
    session_id: "session-rehydrate",
    entry_id: first.session_leaf_id,
    model_config: config,
    workspace_directory: process.cwd(),
    workspace_trusted: true,
  });

  assert.equal(navigation.session_id, "session-rehydrate");
  assert.equal(navigation.session_leaf_id, first.session_leaf_id);
});

test("imports validated Pi JSONL into a relocated session file", async (t) => {
  const home = await mkdtemp(join(tmpdir(), "aether-jsonl-import-"));
  t.after(() => rm(home, { recursive: true, force: true }));
  const client = new BridgeClient({ HOME: home, USERPROFILE: home });
  await client.request("jsonl-create", "run_turn", turnPayload("session-jsonl", [userMessage("hello")]));
  const exported = await client.request("jsonl-export", "export_session_jsonl", {
    session_id: "session-jsonl",
  });
  const jsonl = await readFile(exported.exported_path, "utf8");
  await client.request("jsonl-close", "close_session", {
    session_id: "session-jsonl",
    session_file: exported.exported_path,
    delete_file: true,
  });
  const imported = await client.request("jsonl-import", "import_session_jsonl", {
    session_id: "session-jsonl",
    jsonl,
  });
  assert.equal(imported.imported, true);
  assert.match(imported.session_file, /_session-jsonl\.jsonl$/);
  await assert.rejects(
    client.request("jsonl-invalid", "import_session_jsonl", {
      session_id: "other-session",
      jsonl,
    }),
    /header\/session id mismatch/,
  );
});

test("uses Pi Coding Agent native tool schemas and platform runtime sets", async (t) => {
  const home = await mkdtemp(join(tmpdir(), "aether-native-tools-"));
  const workspace = join(home, "alpine-workspace");
  const termuxWorkspace = join(home, "termux-workspace");
  await Promise.all([mkdir(workspace), mkdir(termuxWorkspace)]);
  t.after(() => rm(home, { recursive: true, force: true }));
  const client = new BridgeClient({ HOME: home, USERPROFILE: home });

  const cases = [
    ["android-alpine", "android", "alpine", ["read", "bash", "edit", "write", "grep", "find", "ls"]],
    ["android-termux", "android", "termux", ["read", "bash", "edit", "write"]],
    ["ios-alpine", "ios", "alpine", ["read", "bash", "edit", "write", "grep", "find", "ls"]],
  ];
  for (const [sessionId, platform, runtime, expectedTools] of cases) {
    await client.request(`${sessionId}-turn`, "run_turn", {
      ...turnPayload(sessionId, [userMessage("hello")]),
      platform,
      runtime,
      workspace_directory: workspace,
      termux_workspace_directory: termuxWorkspace,
    });
    const state = await client.request(`${sessionId}-state`, "get_session_state", {
      session_id: sessionId,
    });
    assert.deepEqual(state.active_tools, expectedTools);
  }

  const state = await client.request("android-alpine-schemas", "get_session_state", {
    session_id: "android-alpine",
  });
  const actualByName = Object.fromEntries(state.tools.map((tool) => [tool.name, tool.parameters]));
  const expectedDefinitions = [
    createReadToolDefinition(workspace),
    createBashToolDefinition(workspace),
    createEditToolDefinition(workspace),
    createWriteToolDefinition(workspace),
    createGrepToolDefinition(workspace),
    createFindToolDefinition(workspace),
    createLsToolDefinition(workspace),
  ];
  for (const definition of expectedDefinitions) {
    assert.deepEqual(actualByName[definition.name], definition.parameters);
  }
  assert.equal(actualByName.read.properties.offset.description.includes("1-indexed"), true);
  assert.equal("working_directory" in actualByName.bash.properties, false);
  assert.equal("environment" in actualByName.bash.properties, false);
});

test("allows only Aether-owned host tools and exposes the platform browser", async (t) => {
  const home = await mkdtemp(join(tmpdir(), "aether-host-tools-"));
  t.after(() => rm(home, { recursive: true, force: true }));
  const client = new BridgeClient({ HOME: home, USERPROFILE: home });
  const sharedNames = [
    "aether_config_get",
    "aether_config_set",
    "aether_skill_manage",
    "aether_extension_manage",
    "aether_developer_manage",
  ];
  const removedNames = [
    "analyze_image",
    "activate_skill",
    "read_skill_resource",
    "fetch_web_url",
    "tavily_search",
    "mcp_call_tool",
    "aether_mcp_manage",
  ];
  const requested = ["browser", ...sharedNames, ...removedNames].map((name) => hostTool(name));

  await client.request("android-host-turn", "run_turn", {
    ...turnPayload("android-host", [userMessage("hello")], fauxConfig(), requested),
    platform: "android",
    chrome_enabled: true,
  });
  const android = await client.request("android-host-state", "get_session_state", {
    session_id: "android-host",
  });
  assert.equal(android.active_tools.includes("browser"), true);
  assert.equal(android.active_tools.includes("chrome"), false);
  assert.deepEqual(sharedNames.filter((name) => android.active_tools.includes(name)), sharedNames);
  assert.deepEqual(removedNames.filter((name) => android.active_tools.includes(name)), []);

  await client.request("ios-host-turn", "run_turn", {
    ...turnPayload("ios-host", [userMessage("hello")], fauxConfig(), requested),
    platform: "ios",
    chrome_enabled: true,
  });
  const ios = await client.request("ios-host-state", "get_session_state", {
    session_id: "ios-host",
  });
  assert.equal(ios.active_tools.includes("browser"), true);
  assert.equal(ios.active_tools.includes("chrome"), false);
  assert.deepEqual(sharedNames.filter((name) => ios.active_tools.includes(name)), sharedNames);
  assert.deepEqual(removedNames.filter((name) => ios.active_tools.includes(name)), []);
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

test("reconnects a failed provider stream without restarting the harness turn", async (t) => {
  let requestCount = 0;
  const server = createServer((request, response) => {
    request.resume();
    request.on("end", () => {
      requestCount += 1;
      response.writeHead(200, { "content-type": "text/event-stream" });
      const content = requestCount === 1 ? "STALE" : "RECOVERED";
      response.write(
        `data: ${JSON.stringify({
          id: `chatcmpl-retry-${requestCount}`,
          object: "chat.completion.chunk",
          created: 1,
          model: "retry-model",
          choices: [
            {
              index: 0,
              delta: { role: "assistant", content },
              finish_reason: null,
            },
          ],
        })}\n\n`,
      );
      if (requestCount === 1) {
        response.end();
        return;
      }
      response.write(
        `data: ${JSON.stringify({
          id: `chatcmpl-retry-${requestCount}`,
          object: "chat.completion.chunk",
          created: 1,
          model: "retry-model",
          choices: [{ index: 0, delta: {}, finish_reason: "stop" }],
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
    "provider-reconnect",
    "run_turn",
    turnPayload(
      "session-provider-reconnect",
      [userMessage("retry this request")],
      {
        provider_type: "openai_compatible",
        provider_config_id: "provider-reconnect",
        pi_provider_id: "aether-retry-test",
        pi_api: "openai-completions",
        model_id: "retry-model",
        base_url: `http://127.0.0.1:${address.port}/v1`,
        api_key: "secret-key",
        reasoning: false,
        max_retries: 2,
        max_retry_delay_ms: 1,
      },
    ),
  );

  assert.equal(requestCount, 2);
  assert.equal(result.assistant_text, "RECOVERED", JSON.stringify(result));
  assert.deepEqual(
    client.events
      .filter(
        (frame) =>
          frame.id === "provider-reconnect" &&
          ["assistant_text_delta", "assistant_stream_reset", "assistant_retry"].includes(
            frame.event,
          ),
      )
      .map((frame) => frame.event),
    ["assistant_text_delta", "assistant_stream_reset", "assistant_retry", "assistant_text_delta"],
  );
});

test("reports Pi AgentSession retry errors", async () => {
  const unavailable = createServer();
  await new Promise((resolve) => unavailable.listen(0, "127.0.0.1", resolve));
  const address = unavailable.address();
  assert.ok(address && typeof address === "object");
  await new Promise((resolve) => unavailable.close(resolve));

  const client = new BridgeClient();
  const run = client.request(
    "provider-network-detail",
    "run_turn",
    turnPayload(
      "session-provider-network-detail",
      [userMessage("show the network failure")],
      {
        provider_type: "openai_compatible",
        provider_config_id: "provider-network-detail",
        pi_provider_id: "aether-network-detail-test",
        pi_api: "openai-completions",
        model_id: "network-detail-model",
        base_url: `http://127.0.0.1:${address.port}/v1`,
        api_key: "secret-key",
        reasoning: false,
        max_retries: 1,
        max_retry_delay_ms: 1,
      },
    ),
  );
  const retry = await client.waitForEvent(
    (frame) => frame.id === "provider-network-detail" && frame.event === "assistant_retry",
  );
  await run;

  assert.equal(typeof retry.payload.error_message, "string");
  assert.notEqual(retry.payload.error_message.trim(), "");
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

test("passes reasoning_effort none when off is selected for a model with thinkingLevelMap off: none", async (t) => {
  let receivedRequest;
  const server = createServer((request, response) => {
    const chunks = [];
    request.on("data", (chunk) => chunks.push(chunk));
    request.on("end", () => {
      receivedRequest = {
        url: request.url,
        authorization: request.headers.authorization,
        body: JSON.parse(Buffer.concat(chunks).toString("utf8")),
      };
      response.writeHead(200, { "content-type": "text/event-stream" });
      response.write(
        `data: ${JSON.stringify({
          id: "chatcmpl-pi-none-test",
          object: "chat.completion.chunk",
          created: 1,
          model: "reasoning-model",
          choices: [
            {
              index: 0,
              delta: { role: "assistant", content: "NONE_OK" },
              finish_reason: null,
            },
          ],
        })}\n\n`,
      );
      response.write(
        `data: ${JSON.stringify({
          id: "chatcmpl-pi-none-test",
          object: "chat.completion.chunk",
          created: 1,
          model: "reasoning-model",
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
    "custom-reasoning-none",
    "complete_once",
    {
      model_config: {
        provider_type: "openai_compatible",
        provider_config_id: "custom-reasoning-none",
        pi_provider_id: "aether-test-reasoning",
        pi_api: "openai-completions",
        model_id: "reasoning-model",
        base_url: `http://127.0.0.1:${address.port}/v1`,
        api_key: "secret-key",
        reasoning: true,
        thinking_level_map: { off: "none" },
      },
      reasoning: "off",
      system_prompt: "Reply briefly.",
      messages: [userMessage("hello")],
      stream: false,
    },
  );

  assert.equal(result.assistant_text, "NONE_OK", JSON.stringify(result));
  assert.equal(receivedRequest.body.reasoning_effort, "none");
});

test("accepts arbitrary manual model IDs for a built-in provider", async (t) => {
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
      model_id: "sfsefehfjksdnf",
      base_url: `http://127.0.0.1:${address.port}/v1`,
      api_key: "secret-key",
      custom_headers: { "X-Aether-Test": "present" },
      reasoning: true,
      max_retries: 0,
    },
    system_prompt: "Reply briefly.",
    messages: [userMessage("hello")],
    reasoning: "high",
    stream: false,
  });

  assert.match(result.error_message, /expected test failure|400/);
  assert.equal(receivedRequest.url, "/v1/responses");
  assert.equal(receivedRequest.authorization, "Bearer secret-key");
  assert.equal(receivedRequest.customHeader, "present");
  assert.equal(receivedRequest.body.model, "sfsefehfjksdnf");
  assert.equal(receivedRequest.body.reasoning.effort, "high");
});

test("omits explicit cache mode for custom OpenAI Responses endpoints", async (t) => {
  const home = await mkdtemp(join(tmpdir(), "aether-custom-responses-"));
  const api = await createOpenAIResponsesServer();
  const client = new BridgeClient({ HOME: home, USERPROFILE: home });
  t.after(async () => {
    await client.close();
    await api.close();
    await rm(home, { recursive: true, force: true });
  });

  const sessionId = "custom-responses-compaction";
  await runResponsesTurns(
    client,
    sessionId,
    openAIResponsesModelConfig(api.baseUrl),
    home,
  );
  const compacted = await client.request(
    "custom-responses-compact",
    "compact_session",
    { session_id: sessionId },
    20_000,
  );

  assert.ok(compacted.compaction);
  assert.equal(api.requests.length, RESPONSES_TEST_TURN_COUNT + 1);
  for (const request of api.requests) {
    assert.equal(request.url, "/v1/responses");
    assert.equal(
      Object.prototype.hasOwnProperty.call(request.body, "prompt_cache_options"),
      false,
    );
  }
});

test("does not replay encrypted reasoning through custom OpenAI Responses gateways", async (t) => {
  const home = await mkdtemp(join(tmpdir(), "aether-responses-replay-"));
  const requests = [];
  const server = createServer((request, response) => {
    const chunks = [];
    request.on("data", (chunk) => chunks.push(chunk));
    request.on("end", () => {
      const body = JSON.parse(Buffer.concat(chunks).toString("utf8"));
      requests.push(body);
      const replayedEncryptedReasoning = body.input?.some(
        (item) => item.type === "reasoning" && item.encrypted_content,
      );
      if (requests.length > 1 && replayedEncryptedReasoning) {
        response.writeHead(400, { "content-type": "application/json" });
        response.end(JSON.stringify({
          error: {
            message: "The encrypted content could not be verified.",
            type: "invalid_request_error",
            code: "invalid_encrypted_content",
          },
        }));
        return;
      }

      response.writeHead(200, { "content-type": "text/event-stream" });
      const events = requests.length === 1
        ? openAIEncryptedReasoningToolEvents()
        : openAIResponseEvents(requests.length);
      for (const event of events) response.write(`data: ${JSON.stringify(event)}\n\n`);
      response.end("data: [DONE]\n\n");
    });
  });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const address = server.address();
  assert.ok(address && typeof address === "object");
  const client = new BridgeClient({ HOME: home, USERPROFILE: home });
  t.after(async () => {
    await client.close();
    await new Promise((resolve) => server.close(resolve));
    await rm(home, { recursive: true, force: true });
  });

  const sessionId = "custom-responses-reasoning-replay";
  const run = client.request(
    "custom-responses-reasoning-turn",
    "run_turn",
    {
      ...turnPayload(
        sessionId,
        [userMessage("Use the settings tool, then answer.")],
        openAIResponsesModelConfig(`http://127.0.0.1:${address.port}/v1`, {
          provider_config_id: sessionId,
        }),
        [hostTool("aether_config_get")],
      ),
      workspace_directory: home,
      session_directory: home,
      reasoning: "high",
      max_retries: 0,
    },
    20_000,
  );
  const hostCall = await client.waitForEvent(
    (frame) => frame.id === "custom-responses-reasoning-turn" && frame.event === "host_tool_request",
  );
  await respondToHostTool(client, hostCall, "custom-responses-reasoning-tool-result");
  const result = await run;

  assert.equal(result.assistant_text, "answer-2", JSON.stringify(result));
  assert.equal(requests.length, 2);
  assert.ok(requests[0].include.includes("reasoning.encrypted_content"));
  assert.equal(
    requests[1].input.some((item) => item.type === "reasoning" && item.encrypted_content),
    false,
  );
  const replayedFunctionCall = requests[1].input.find((item) => item.type === "function_call");
  assert.ok(replayedFunctionCall);
  assert.equal(Object.hasOwn(replayedFunctionCall, "id"), false);
});

test("preserves explicit cache mode for the official OpenAI Responses endpoint", async (t) => {
  const home = await mkdtemp(join(tmpdir(), "aether-official-responses-"));
  const api = await createOpenAIResponsesServer();
  const client = new BridgeClient({
    HOME: home,
    USERPROFILE: home,
    ...openAIFetchRedirectEnvironment(api.baseUrl),
  });
  t.after(async () => {
    await client.close();
    await api.close();
    await rm(home, { recursive: true, force: true });
  });

  const sessionId = "official-responses-compaction";
  await runResponsesTurns(
    client,
    sessionId,
    openAIResponsesModelConfig("https://API.OPENAI.COM:443/v1/", {
      provider_config_id: sessionId,
    }),
    home,
  );
  const compacted = await client.request(
    "official-responses-compact",
    "compact_session",
    { session_id: sessionId },
    20_000,
  );

  assert.ok(compacted.compaction);
  assert.equal(api.requests.length, RESPONSES_TEST_TURN_COUNT + 1);
  for (const request of api.requests) {
    assert.equal(request.url, "/v1/responses");
  }
  for (const request of api.requests.slice(0, -1)) {
    assert.equal(
      Object.prototype.hasOwnProperty.call(request.body, "prompt_cache_options"),
      false,
    );
  }
  assert.deepEqual(api.requests.at(-1).body.prompt_cache_options, { mode: "explicit" });
});

test("lists every built-in Pi provider and its model catalog", async () => {
  const client = new BridgeClient();
  const catalog = await client.request("providers", "list_providers");
  const providers = catalog.providers;

  assert.equal(providers.length, 39);
  assert.equal(new Set(providers.map((provider) => provider.id)).size, 39);
  assert.ok(providers.every((provider) => provider.models.length > 0));
  assert.ok(providers.every((provider) => provider.models.every((model) => model.id)));

  const oauthProviders = providers
    .filter((provider) => provider.auth.oauth)
    .map((provider) => provider.id)
    .sort();
  assert.deepEqual(oauthProviders, [
    "anthropic",
    "github-copilot",
    "kimi-coding",
    "openai-codex",
    "openrouter",
    "xai",
  ]);
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

test("keeps Codex browser OAuth ready for an intercepted loopback redirect", async () => {
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
  assert.match(authUrl.payload.instructions, /authentication window/i);
  assert.equal(manualPrompt.payload.placeholder, "http://localhost:...");

  const state = new URL(authUrl.payload.url).searchParams.get("state");
  assert.equal(new URL(authUrl.payload.url).searchParams.get("redirect_uri"), "http://localhost:1455/auth/callback");

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

  const bedrockLogin = client.request("api-key-bedrock", "login_provider", {
    provider_id: "amazon-bedrock",
    provider_config_id: `test-${"amazon-bedrock"}`,
    auth_method: "api_key",
  });
  const bedrockPrompt = await client.waitForEvent(
    (frame) =>
      frame.id === "api-key-bedrock" &&
      frame.event === "auth_prompt" &&
      frame.payload.prompt_type === "select",
  );
  assert.deepEqual(
    bedrockPrompt.payload.options.map((option) => option.id),
    ["bearer-token", "aws-profile", "credential-chain"],
  );
  await client.request("api-key-bedrock-cancel", "auth_prompt_result", {
    prompt_id: bedrockPrompt.payload.prompt_id,
    cancelled: true,
  });
  await assert.rejects(bedrockLogin, /cancel/i);
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
