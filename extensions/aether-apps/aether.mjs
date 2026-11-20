import crypto from "node:crypto";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { buildZipFromDirectory } from "./zip.mjs";

const EXTENSION_DIR = path.dirname(fileURLToPath(import.meta.url));
const DEMO_APP = {
  id: "decision-lab",
  label: "Decision Lab",
  version: "1.0.1",
  dir: path.join(EXTENSION_DIR, "demo", "decision-lab"),
};
const DATA_DIR = path.join(os.homedir(), ".aether", "hosted-app-data");
const SETTINGS_PAGE_ID = "apps";

function ensureDataDir() {
  fs.mkdirSync(DATA_DIR, { recursive: true });
}

function appStorageFile(appId) {
  const safe = String(appId || "").replace(/[^a-z0-9-]/g, "");
  if (!safe) throw new Error("Invalid app id.");
  ensureDataDir();
  return path.join(DATA_DIR, `${safe}.json`);
}

function readAppStore(appId) {
  const file = appStorageFile(appId);
  if (!fs.existsSync(file)) return {};
  try {
    const parsed = JSON.parse(fs.readFileSync(file, "utf8"));
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) ? parsed : {};
  } catch {
    return {};
  }
}

function writeAppStore(appId, store) {
  const serialized = JSON.stringify(store);
  if (Buffer.byteLength(serialized, "utf8") > 2 * 1024 * 1024) {
    throw new Error("App storage exceeds the 2 MiB limit.");
  }
  const file = appStorageFile(appId);
  const tmp = `${file}.${crypto.randomUUID()}.tmp`;
  fs.writeFileSync(tmp, serialized, "utf8");
  fs.renameSync(tmp, file);
}

async function uploadZipToHostedApps(api, { id, label, version, buffer, sha256 }) {
  const begin = await api.services.invoke("hosted_apps", "begin", {
    id,
    label,
    version,
    bytes: buffer.length,
    sha256,
  });
  const upload = begin.upload;
  const chunkBytes = Math.min(65536, Number(begin.chunkBytes) || 65536);
  try {
    for (let offset = 0; offset < buffer.length; offset += chunkBytes) {
      const slice = buffer.subarray(offset, offset + chunkBytes);
      await api.services.invoke("hosted_apps", "chunk", {
        upload,
        offset,
        base64: slice.toString("base64"),
      });
    }
    return await api.services.invoke("hosted_apps", "commit", { upload });
  } catch (error) {
    await api.services.invoke("hosted_apps", "cancel", { upload }).catch(() => {});
    throw error;
  }
}

export const activateAether = async (api) => {
  const ui = api.ui;
  const activeRequests = new Map();
  const state = {
    visible: false,
    busy: false,
    mode: "library", // library | ai-settings | model-picker | effort-picker
    selectedAppId: "",
    error: "",
    notice: "",
    capabilities: null,
    apps: [],
    catalog: [],
    activeSelection: null,
    sharedFolderUri: api.storage.get("sharedFolderUri", ""),
  };

  const getPrefsMap = () => {
    const map = api.storage.get("appModelPreferences", {});
    return map && typeof map === "object" ? { ...map } : {};
  };

  const getAppPreference = (appId) => {
    const pref = getPrefsMap()[appId];
    return pref && typeof pref === "object" ? { ...pref } : {};
  };

  const setAppPreference = (appId, pref) => {
    const map = getPrefsMap();
    if (!pref || (!pref.modelKey && !pref.thinkingLevel)) {
      delete map[appId];
    } else {
      map[appId] = pref;
    }
    api.storage.set("appModelPreferences", map);
  };

  const invokeService = (service, method, args = {}) =>
    api.services.invoke(service, method, args);

  async function refreshState() {
    try {
      state.capabilities = await invokeService("hosted_apps", "capabilities", {});
      const listed = await invokeService("hosted_apps", "list", {});
      state.apps = Array.isArray(listed.apps) ? listed.apps : [];
    } catch (err) {
      state.capabilities = { supported: false, enabled: false, reason: err?.message || String(err) };
      state.apps = [];
    }
    try {
      state.activeSelection = await invokeService("model_access", "selection", {});
    } catch {
      state.activeSelection = null;
    }
    syncSettingsPage();
    api.invalidate();
  }

  async function installDemoApp(force = false) {
    if (!fs.existsSync(path.join(DEMO_APP.dir, "index.html"))) {
      throw new Error("Bundled Decision Lab demo files are missing.");
    }
    const existing = state.apps.find((a) => a.id === DEMO_APP.id);
    if (existing && !force) return existing;
    const { buffer, sha256 } = buildZipFromDirectory(DEMO_APP.dir);
    const version = existing && existing.version === DEMO_APP.version
      ? `${DEMO_APP.version}-${Date.now()}`
      : DEMO_APP.version;
    const installed = await uploadZipToHostedApps(api, {
      id: DEMO_APP.id,
      label: DEMO_APP.label,
      version,
      buffer,
      sha256,
    });
    api.storage.set("demoUninstalled", false);
    await refreshState();
    return installed;
  }

  async function importZipApp() {
    const picked = await invokeService("documents", "chooseFile", {
      mimeTypes: ["application/zip", "application/octet-stream"],
    });
    if (picked.cancelled || !picked.uri) return null;
    const uri = picked.uri;
    const stat = await invokeService("documents", "stat", { tree: uri, path: "" });
    if (!stat.exists) throw new Error("Selected ZIP file could not be read.");
    const totalBytes = Number(stat.bytes) || 0;
    if (totalBytes <= 0 || totalBytes > 32 * 1024 * 1024) {
      throw new Error("ZIP size must be between 1 byte and 32 MiB.");
    }
    const chunks = [];
    let offset = 0;
    while (offset < totalBytes) {
      const part = await invokeService("documents", "read", {
        tree: uri,
        path: "",
        offset,
        limit: 65536,
      });
      const buf = Buffer.from(part.base64 || "", "base64");
      if (buf.length === 0) break;
      chunks.push(buf);
      offset += buf.length;
    }
    const zipBuffer = Buffer.concat(chunks);
    const sha256 = crypto.createHash("sha256").update(zipBuffer).digest("hex");
    const slug = `app-${sha256.slice(0, 8)}`;
    const installed = await uploadZipToHostedApps(api, {
      id: slug,
      label: `Web App (${sha256.slice(0, 6)})`,
      version: `import-${Date.now()}`,
      buffer: zipBuffer,
      sha256,
    });
    await refreshState();
    return installed;
  }

  async function runBusy(work) {
    if (state.busy) return;
    state.busy = true;
    state.error = "";
    api.invalidate();
    try {
      await work();
    } catch (err) {
      state.error = String(err?.message || err).slice(0, 600);
      api.notify(state.error, "error");
    } finally {
      state.busy = false;
      syncSettingsPage();
      api.invalidate();
    }
  }

  function hostingSummaryText() {
    if (!state.capabilities) return "Checking Android web-app host status…";
    if (!state.capabilities.enabled) return "Native web-app hosting is disabled.";
    if (!state.capabilities.supported) return state.capabilities.reason || "Web-app hosting is unavailable on this device.";
    const model = state.activeSelection?.model?.id
      ? `${state.activeSelection.model.id}${state.activeSelection.thinkingLevel && state.activeSelection.thinkingLevel !== "off" ? ` (${state.activeSelection.thinkingLevel})` : ""}`
      : "No model selected";
    return `Ready · ${state.apps.length} app(s) installed · Active model: ${model}`;
  }

  function syncSettingsPage() {
    const enabled = state.capabilities ? state.capabilities.enabled !== false : true;
    const demoInstalled = state.apps.some((a) => a.id === DEMO_APP.id);
    api.storage.set(`settings:${SETTINGS_PAGE_ID}:hosting_enabled`, enabled);

    api.registerSettings({
      id: SETTINGS_PAGE_ID,
      title: "Apps",
      subtitle: "Local AI web apps · standalone Android windows",
      icon: "extension",
      order: 30,
      sections: [
        {
          title: "Apps & Demo",
          description: hostingSummaryText(),
          settings: [
            {
              id: "launch_demo",
              type: "button",
              label: demoInstalled ? "Open Decision Lab (Demo)" : "Install & Open Decision Lab (Demo)",
              description: "Interactive AI scenario simulator & flashcard studio with streaming and local persistence.",
              action: "launch-demo",
              tone: "primary",
              icon: "play",
            },
            {
              id: "open_library",
              type: "button",
              label: "Open Apps Library",
              description: "Manage installed apps, per-app AI model preferences, home-screen pins, and ZIP imports.",
              action: "open-apps",
              tone: "neutral",
              icon: "extension",
            },
            {
              id: "hosting_enabled",
              type: "toggle",
              label: "Enable Native Web App Hosting",
              description: "Allow opening hosted web apps in standalone Android document windows.",
              default: enabled,
            },
          ],
        },
        {
          title: "Actions",
          settings: [
            {
              id: "pin_demo",
              type: "button",
              label: "Pin Decision Lab to Home Screen",
              description: "Creates an Android launcher shortcut that opens Decision Lab directly.",
              action: "pin-demo",
              tone: "neutral",
              icon: "home",
            },
            {
              id: "import_zip",
              type: "button",
              label: "Import Static Web App ZIP",
              description: "Pick a local ZIP containing index.html to install as a hosted app.",
              action: "import-zip",
              tone: "neutral",
              icon: "add",
            },
            {
              id: "close_all",
              type: "button",
              label: "Close All App Windows",
              action: "close-all",
              tone: "neutral",
            },
            {
              id: "reinstall_demo",
              type: "button",
              label: "Reinstall Decision Lab Demo",
              action: "reinstall-demo",
              tone: "neutral",
              icon: "refresh",
            },
            {
              id: "uninstall_demo",
              type: "button",
              label: "Uninstall Decision Lab Demo",
              description: "Removes hosted assets, clears origin storage, and disables its shortcut.",
              action: "uninstall-demo",
              tone: "danger",
              icon: "delete",
            },
          ],
        },
      ],
    });
  }

  // ---------- Capability Gateway for Hosted WebViews ----------
  async function handleHostedRequest(request) {
    const { requestId, app, method, args = {} } = request;
    if (method === "ai.selection") {
      const pref = getAppPreference(app.id);
      return await invokeService("model_access", "selection", { preference: pref });
    }
    if (method === "ai.catalog") {
      return await invokeService("model_access", "catalog", {});
    }
    if (method === "ai.complete") {
      const pref = getAppPreference(app.id);
      return await invokeService("model_access", "complete", {
        request_handle: requestId,
        preference: pref,
        expected: args.expected,
        context: args.context,
        maxTokens: args.maxTokens,
        stream: Boolean(args.stream),
        caller: app.id,
        session_id: typeof args.session_id === "string" ? args.session_id : "",
      });
    }
    if (method === "storage.get") {
      const store = readAppStore(app.id);
      const key = String(args.key || "");
      return { exists: Object.prototype.hasOwnProperty.call(store, key), value: store[key] ?? null };
    }
    if (method === "storage.set") {
      const key = String(args.key || "");
      if (!key || key.length > 128) throw new Error("Invalid storage key.");
      const store = readAppStore(app.id);
      store[key] = args.value ?? null;
      writeAppStore(app.id, store);
      return { saved: true };
    }
    if (method === "storage.delete") {
      const key = String(args.key || "");
      const store = readAppStore(app.id);
      delete store[key];
      writeAppStore(app.id, store);
      return { deleted: true };
    }
    if (method === "storage.list") {
      const store = readAppStore(app.id);
      return { keys: Object.keys(store) };
    }
    throw new Error(`Unsupported hosted app capability: ${method}`);
  }

  api.on("hosted_apps.request", (request) => {
    if (!request || typeof request.requestId !== "string" || !request.app?.id) return;
    if (activeRequests.size >= 16) {
      void invokeService("hosted_apps", "reply", {
        requestId: request.requestId,
        ok: false,
        error: "App capability queue is full.",
      }).catch(() => {});
      return;
    }
    const marker = { cancelled: false };
    activeRequests.set(request.requestId, marker);
    void handleHostedRequest(request)
      .then(
        (result) =>
          invokeService("hosted_apps", "reply", {
            requestId: request.requestId,
            ok: true,
            result,
          }),
        (err) =>
          invokeService("hosted_apps", "reply", {
            requestId: request.requestId,
            ok: false,
            error: String(err?.message || err).slice(0, 600),
          }),
      )
      .catch(() => {})
      .finally(() => activeRequests.delete(request.requestId));
  });

  api.on("hosted_apps.cancel", ({ requestId }) => {
    const marker = activeRequests.get(requestId);
    if (marker) marker.cancelled = true;
    void invokeService("model_access", "cancel", { request_handle: requestId }).catch(() => {});
  });

  // ---------- Actions ----------
  api.registerAction(`settings:${SETTINGS_PAGE_ID}:hosting_enabled`, async ({ value }) => {
    await runBusy(async () => {
      await invokeService("hosted_apps", "setEnabled", { enabled: value === true });
      await refreshState();
      api.notify(value === true ? "Native web-app hosting enabled." : "Native web-app hosting disabled.");
    });
  });

  api.registerAction("open-apps", async () => {
    state.visible = true;
    state.mode = "library";
    state.error = "";
    state.notice = "";
    api.invalidate();
    await runBusy(refreshState);
  });

  api.registerAction("close-apps", () => {
    state.visible = false;
    api.invalidate();
  });

  api.registerAction("back-apps", () => {
    if (state.mode === "model-picker" || state.mode === "effort-picker") {
      state.mode = "ai-settings";
    } else if (state.mode !== "library") {
      state.mode = "library";
    } else {
      state.visible = false;
    }
    api.invalidate();
  });

  api.registerAction("refresh-apps", () => runBusy(refreshState));

  api.registerAction("launch-demo", () =>
    runBusy(async () => {
      await refreshState();
      if (state.capabilities?.enabled === false) {
        await invokeService("hosted_apps", "setEnabled", { enabled: true });
        await refreshState();
      }
      await installDemoApp(false);
      await invokeService("hosted_apps", "open", { id: DEMO_APP.id });
      state.visible = false;
    }),
  );

  api.registerAction("reinstall-demo", () =>
    runBusy(async () => {
      await installDemoApp(true);
      state.notice = "Decision Lab demo reinstalled.";
      api.notify("Decision Lab demo reinstalled.");
    }),
  );

  api.registerAction("uninstall-demo", () =>
    runBusy(async () => {
      await invokeService("hosted_apps", "uninstall", { id: DEMO_APP.id });
      api.storage.set("demoUninstalled", true);
      await refreshState();
      api.notify("Decision Lab demo uninstalled.");
    }),
  );

  api.registerAction("pin-demo", () =>
    runBusy(async () => {
      await refreshState();
      await installDemoApp(false);
      const res = await invokeService("hosted_apps", "pin", { id: DEMO_APP.id });
      api.notify(res.requested ? "Shortcut request sent to launcher." : (res.reason || "Pinning unavailable."));
    }),
  );

  api.registerAction("import-zip", () =>
    runBusy(async () => {
      const app = await importZipApp();
      if (app) {
        state.notice = `Installed ${app.label} (${app.id}).`;
        api.notify(`Installed ${app.label}`);
      }
    }),
  );

  api.registerAction("close-all", () =>
    runBusy(async () => {
      await invokeService("hosted_apps", "close", {});
      api.notify("Closed all hosted app windows.");
    }),
  );

  api.registerAction("open-app", ({ id }) =>
    runBusy(async () => {
      await invokeService("hosted_apps", "open", { id: String(id) });
      state.visible = false;
    }),
  );

  api.registerAction("pin-app", ({ id }) =>
    runBusy(async () => {
      const res = await invokeService("hosted_apps", "pin", { id: String(id) });
      api.notify(res.requested ? "Shortcut request sent to launcher." : (res.reason || "Pinning unavailable."));
    }),
  );

  api.registerAction("uninstall-app", ({ id }) =>
    runBusy(async () => {
      const appId = String(id);
      await invokeService("hosted_apps", "uninstall", { id: appId });
      if (appId === DEMO_APP.id) api.storage.set("demoUninstalled", true);
      await refreshState();
      api.notify(`Uninstalled ${appId}`);
    }),
  );

  api.registerAction("open-ai-settings", ({ id }) =>
    runBusy(async () => {
      state.selectedAppId = String(id);
      const cat = await invokeService("model_access", "catalog", {});
      state.catalog = Array.isArray(cat.models) ? cat.models : [];
      state.mode = "ai-settings";
    }),
  );

  api.registerAction("open-model-picker", () => {
    state.mode = "model-picker";
    api.invalidate();
  });

  api.registerAction("open-effort-picker", () => {
    state.mode = "effort-picker";
    api.invalidate();
  });

  api.registerAction("select-model", ({ key }) => {
    const appId = state.selectedAppId;
    const pref = getAppPreference(appId);
    if (!key) {
      delete pref.modelKey;
      delete pref.thinkingLevel;
    } else {
      pref.modelKey = String(key);
      delete pref.thinkingLevel;
    }
    setAppPreference(appId, pref);
    state.mode = "ai-settings";
    api.invalidate();
  });

  api.registerAction("select-effort", ({ level }) => {
    const appId = state.selectedAppId;
    const pref = getAppPreference(appId);
    if (!level) delete pref.thinkingLevel;
    else pref.thinkingLevel = String(level);
    setAppPreference(appId, pref);
    state.mode = "ai-settings";
    api.invalidate();
  });

  // ---------- Native Full-Screen Library Overlay (uses screen + listItem) ----------
  function renderOverlayChildren() {
    const children = [];
    if (state.error) {
      children.push(
        ui.card([ui.text("Error", { weight: "semibold" }), ui.text(state.error)], { tone: "error", radius: 18 }),
      );
    }
    if (state.notice) {
      children.push(ui.text(state.notice, { style: "small", color: "muted" }));
    }

    if (state.mode === "library") {
      children.push(
        ui.row(
          [
            ui.button("Launch Demo", "launch-demo", { icon: "play", enabled: !state.busy }),
            ui.button("Import ZIP", "import-zip", { variant: "outlined", icon: "add", enabled: !state.busy }),
            ui.iconButton("refresh", "refresh-apps", { contentDescription: "Refresh", enabled: !state.busy }),
          ],
          { wrap: true, rowSpacing: 8 },
        ),
      );
      children.push(ui.text(hostingSummaryText(), { style: "small", color: "muted" }));
      if (state.apps.length === 0) {
        children.push(
          ui.card(
            [
              ui.text("No hosted apps installed yet", { style: "subtitle", weight: "semibold" }),
              ui.text("Tap Launch Demo to install Decision Lab, or import any static web-app ZIP."),
            ],
            { radius: 20 },
          ),
        );
      } else {
        for (const app of state.apps) {
          const pref = getAppPreference(app.id);
          const prefLabel = pref.modelKey
            ? `Custom model · ${pref.thinkingLevel || "default effort"}`
            : "Follows Aether active model";
          children.push(
            ui.card(
              [
                ui.node("listItem", {
                  label: app.label,
                  subtitle: `${app.id} · v${app.version} · ${prefLabel}`,
                  icon: "extension",
                  action: "open-app",
                  args: { id: app.id },
                  enabled: !state.busy,
                }),
                ui.row(
                  [
                    ui.button("Open", "open-app", { args: { id: app.id }, icon: "play", enabled: !state.busy }),
                    ui.button("Model", "open-ai-settings", { args: { id: app.id }, variant: "outlined", enabled: !state.busy }),
                    ui.button("Pin", "pin-app", { args: { id: app.id }, variant: "text", enabled: !state.busy }),
                    ui.button("Remove", "uninstall-app", { args: { id: app.id }, variant: "text", tone: "danger", enabled: !state.busy }),
                  ],
                  { wrap: true, rowSpacing: 6 },
                ),
              ],
              { radius: 20, contentPadding: 12, spacing: 8 },
            ),
          );
        }
      }
      return children;
    }

    if (state.mode === "ai-settings") {
      const pref = getAppPreference(state.selectedAppId);
      const chosen = state.catalog.find((m) => m.key === pref.modelKey);
      children.push(
        ui.node("listItem", {
          label: "Model",
          subtitle: chosen ? chosen.label : "Follow Aether active model",
          icon: "sparkles",
          action: "open-model-picker",
          enabled: !state.busy,
        }),
      );
      if (chosen && Array.isArray(chosen.thinkingLevels) && chosen.thinkingLevels.length > 0) {
        children.push(
          ui.node("listItem", {
            label: "Thinking level",
            subtitle: pref.thinkingLevel || "Default",
            icon: "settings",
            action: "open-effort-picker",
            enabled: !state.busy,
          }),
        );
      }
      return children;
    }

    if (state.mode === "model-picker") {
      const pref = getAppPreference(state.selectedAppId);
      children.push(
        ui.node("listItem", {
          label: "Follow Aether active model",
          subtitle: "Use whichever model is active in Aether",
          action: "select-model",
          args: { key: "" },
          selected: !pref.modelKey,
          enabled: !state.busy,
        }),
      );
      for (const m of state.catalog) {
        children.push(
          ui.node("listItem", {
            label: m.label,
            subtitle: m.key,
            action: "select-model",
            args: { key: m.key },
            selected: pref.modelKey === m.key,
            enabled: !state.busy,
          }),
        );
      }
      return children;
    }

    if (state.mode === "effort-picker") {
      const pref = getAppPreference(state.selectedAppId);
      const chosen = state.catalog.find((m) => m.key === pref.modelKey);
      const levels = Array.isArray(chosen?.thinkingLevels) ? chosen.thinkingLevels : [];
      children.push(
        ui.node("listItem", {
          label: "Default",
          action: "select-effort",
          args: { level: "" },
          selected: !pref.thinkingLevel,
          enabled: !state.busy,
        }),
      );
      for (const level of levels) {
        children.push(
          ui.node("listItem", {
            label: String(level),
            action: "select-effort",
            args: { level: String(level) },
            selected: pref.thinkingLevel === level,
            enabled: !state.busy,
          }),
        );
      }
      return children;
    }

    return children;
  }

  api.registerSurface("app.overlay", {
    id: "apps-library",
    order: 25,
    render: () => {
      if (!state.visible) return null;
      const titles = {
        library: "Apps",
        "ai-settings": `AI Model · ${state.selectedAppId}`,
        "model-picker": "Choose Model",
        "effort-picker": "Thinking Level",
      };
      return ui.node("screen", {
        id: `apps-${state.mode}-${state.selectedAppId}`,
        title: titles[state.mode] || "Apps",
        backAction: state.mode === "library" ? "" : "back-apps",
        closeAction: "close-apps",
        enabled: !state.busy,
        children: renderOverlayChildren(),
      });
    },
  });

  // Register Settings immediately so "Apps" appears right away in Settings -> Extensions
  syncSettingsPage();

  // Warm state & auto-install the built-in Decision Lab demo once on Android hosts
  const initTimer = setTimeout(() => {
    void (async () => {
      try {
        await refreshState();
        const installedDemo = state.apps.find((a) => a.id === DEMO_APP.id);
        if (
          state.capabilities?.supported === true &&
          !api.storage.get("demoUninstalled", false) &&
          (!installedDemo || installedDemo.version !== DEMO_APP.version)
        ) {
          await installDemoApp(Boolean(installedDemo));
        }
      } catch {
        // Non-fatal on startup; user can trigger manually from Settings -> Extensions -> Apps
      }
    })();
  }, 500);

  return () => {
    clearTimeout(initTimer);
    for (const requestId of activeRequests.keys()) {
      void invokeService("model_access", "cancel", { request_handle: requestId }).catch(() => {});
    }
    activeRequests.clear();
  };
};
