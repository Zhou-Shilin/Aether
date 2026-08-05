# Aether Extensions and Mod Kernel

Aether follows Pi Coding Agent's trusted-code extension model, but adds an
Android-specific Mod Kernel. One package can contain any combination of:

- `pi.extensions`: Pi-compatible Agent tools, commands, hooks, and logic.
- `aether.extensions`: hot-reloadable TypeScript/JavaScript UI and app logic.
- `aether.native`: restart-loaded Kotlin/DEX code with direct Android and
  Compose access.

There is intentionally no permission sandbox. Installing an extension means
trusting it with the Node runtime, Android application context, Aether services,
local runtimes, and workspace. Native Mods can use reflection or Android APIs
directly; the public registries are convenience and interoperability APIs, not
security boundaries.

## Package format

```json
{
  "name": "my-aether-mod",
  "version": "1.0.0",
  "pi": {
    "extensions": ["./agent.ts"]
  },
  "aether": {
    "api": {
      "min": 2,
      "max": 2
    },
    "extensions": ["./android.ts"],
    "native": {
      "api": {
        "min": 1,
        "max": 1
      },
      "classpath": ["./native/mod.dex"],
      "libraryPath": ["./native/lib"],
      "entrypoints": ["com.example.aether.MyNativeMod"]
    }
  }
}
```

Script-only packages do not need `aether.native`. Native-only zip packages are
also importable. Imported packages live under `~/.aether/extensions` inside
Aether's managed Alpine filesystem.

Script files under `~/.aether/extensions`, `~/.pi/agent/extensions`, and
installed package entrypoint directories are watched and reloaded
automatically. Reloads are atomic: all Script factories must load successfully
before the new runtime replaces the previous one. A failed load or cleanup is
reported on the Extensions page, and a rejected load keeps the last working
runtime active.

Native classpaths from imported extensions and npm-installed packages are
discovered and loaded only during app process startup, so installing, updating,
removing, or changing a Native Mod requires restarting Aether.

ZIP imports install runtime npm dependencies before replacing the currently
installed copy. A bundled `node_modules` directory is used as-is; otherwise
Aether runs `npm ci --omit=dev` when a lockfile is present and
`npm install --omit=dev` when it is not. Failed dependency installation or
runtime reload restores the previous imported package.

## Script Mod API v2

Install the declarations for editor completion and TypeScript checking:

```bash
npm install --save-dev @baimoqilin/aether-extension-api
```

The npm package contains declarations only. Aether supplies the implementation
through its trusted Script runtime.

```ts
interface AetherExtensionAPI {
  readonly apiVersion: 2;
  readonly extension: { id: string; name: string; path: string };
  readonly ui: typeof ui;
  readonly host: {
    invoke(method: string, args?: object): Promise<object>;
  };
  readonly services: {
    list(): Promise<object>;
    describe(service: string): Promise<object>;
    invoke(service: string, method: string, args?: object): Promise<object>;
  };
  readonly state: {
    get(path?: string): Promise<object>;
    patch(path: string, value: unknown): Promise<object>;
    transaction(operations: Array<{
      op?: "set" | "remove";
      path: string;
      value?: unknown;
    }>): Promise<object>;
  };
  readonly storage: {
    get<T>(key: string, fallback?: T): T;
    set(key: string, value: unknown): void;
    delete(key: string): void;
    clear(): void;
    snapshot(): object;
  };

  registerSurface(slot: string, definition: SurfaceDefinition): () => void;
  registerComponent(target: string, definition: ComponentDefinition): () => void;
  registerPage(definition: PageDefinition): () => void;
  registerAction(id: string, handler: ActionHandler): () => void;
  on(event: string, handler: EventHandler): () => void;
  intercept(operation: string, handler: EventHandler): () => void;
  invalidate(): void;
  notify(message: string, level?: "info" | "warning" | "error"): void;
}
```

Factories may be async and may return a cleanup function. Extension storage is
persisted in `~/.aether/app-extension-state.json`.

### API compatibility

Script packages may constrain the integer Script API version:

```json
{
  "aether": {
    "api": {
      "min": 2,
      "max": 2,
      "allowNewer": false
    }
  }
}
```

An exact integer such as `"api": 2` is also accepted. Missing declarations
remain compatible for legacy packages. An incompatible or malformed range
rejects the atomic reload and leaves the last working runtime active.

Native Mods use an independent version range under `aether.native.api`.
The current Native API version is `1`. `allowNewer: true` lets a Mod opt into
loading on a runtime newer than its declared maximum.

For a package shared with Pi, keep the Pi factory as the default export and
export the Aether factory as `activateAether` or `aether`:

```ts
import type { ExtensionAPI } from "@earendil-works/pi-coding-agent";
import { defineAetherExtension, ui } from "@baimoqilin/aether-extension-api";

export default function activatePi(pi: ExtensionAPI) {
  pi.registerCommand("hello", {
    description: "Pi-compatible command",
    handler: async (_args, ctx) => ctx.ui.notify("Hello from Pi", "info"),
  });
}

export const activateAether = defineAetherExtension((aether) => {
  aether.registerSurface("chat.composer.top", {
    id: "hello",
    render: () => ui.button("Insert prompt", "insert"),
  });
  aether.registerAction("insert", () =>
    aether.state.patch("draft_input", "Explain this repository.")
  );
});
```

## UI surfaces

Surfaces add content without replacing the built-in UI.

| Slot | Location |
| --- | --- |
| `app.overlay` | Full application overlay |
| `chat.top` | Under the chat top bar |
| `chat.empty` | Empty-conversation surface |
| `chat.list.start` | Before committed messages |
| `chat.list.end` | After messages and pending work |
| `chat.composer.top` | Directly above the composer |
| `settings.hub` | Top of the settings hub |
| `drawer.header` | Fixed below the drawer title and search controls |
| `drawer.footer` | Fixed full-width content above the floating new-chat action |
| `drawer.list.end` | After the conversation list |
| `drawer` | Legacy conversation-drawer list content |

```ts
aether.registerSurface("chat.composer.top", {
  id: "status",
  order: 10,
  render: ({ is_running, draft_input, storage }) =>
    ui.row([
      ui.text(is_running ? "Agent running" : "Ready"),
      ui.text(`${String(draft_input).length} chars`),
      ui.text(`Count: ${storage.count ?? 0}`),
    ]),
});
```

The drawer header and footer remain fixed while the conversation list scrolls.
The legacy `drawer` slot remains supported and is rendered at the list tail in
this order: `drawer`, `drawer.list.end`, then built-in extension page launchers.

The `drawer.opened` event carries an empty event payload and the current
extension context. On mobile it fires once for each closed-to-open transition,
including swipe gestures. On tablet it fires once when the permanent drawer
enters composition; stable-open recomposition does not repeat it.

```ts
aether.on("drawer.opened", (_data, context) => {
  aether.notify(`Drawer opened on ${context.screen}`);
});
```

## Replace, wrap, hide, before, and after

Components modify a named built-in Compose target:

| Target | Built-in UI |
| --- | --- |
| `app.content` | Entire routed Aether application content |
| `chat.screen` | Complete chat screen |
| `settings.screen` | Complete settings screen |
| `chat.composer.actionTray` | Selected Skill/MCP/Agent Mode tray |
| `chat.composer.skillPicker` | Skill rows in the composer plus menu |

Supported modes are `before`, `after`, `replace`, `wrap`, and `hide`.
Registrations are ordered by `order`; the last `replace` or `hide` is decisive.
Wrappers are then composed around that center.

```ts
aether.registerComponent("chat.composer.actionTray", {
  id: "replacement",
  mode: "replace",
  order: 100,
  render: () => ui.card([
    ui.text("My replacement tray"),
  ]),
});

aether.registerComponent("chat.composer.skillPicker", {
  id: "remove-native-picker",
  mode: "hide",
  order: 100,
});
```

A wrapper places `ui.core()` where the wrapped native or replacement content
should render:

```ts
aether.registerComponent("chat.composer.actionTray", {
  id: "wrapper",
  mode: "wrap",
  render: () => ui.column([
    ui.text("Before the original"),
    ui.core(),
    ui.text("After the original"),
  ]),
});
```

Additional targets can be introduced by Aether core or by future component
registries without changing the API.

## Declarative UI tree and WebView escape hatch

Supported native node types include:

- `text`, `code`
- `column`, `row`, `box`, `scroll`
- `card`
- `button`, `iconButton`
- `switch`, `input`
- `spacer`, `progress`
- `pageButton`
- `web`
- `core` for wrapper nesting

Rows fill the available width and remain on one line by default. Set
`wrap: true` for narrow Android layouts. `rowSpacing` controls wrapped-line
spacing and `maxItemsInEachRow` caps each line. Button labels render on one
line; an explicit `width` still overrides fill behavior. A direct child of a
non-wrapping row may set `weight` to consume only the remaining horizontal
space while preserving intrinsic-width controls beside it.

Actions are extension-local:

```ts
aether.registerAction("increment", ({ amount = 1 }) => {
  const count = aether.storage.get("count", 0) + Number(amount);
  aether.storage.set("count", count);
});

ui.button("Increment", "increment", {
  args: { amount: 1 },
});
```

For unrestricted HTML micro-UIs, use a WebView node:

```ts
ui.web({
  height: 320,
  html: `
    <button onclick='Aether.postMessage(JSON.stringify({
      action: "clicked",
      args: { source: "web" }
    }))'>Run</button>
  `,
});
```

JavaScript, DOM storage, network access, file access, and content access are
enabled. `Aether.postMessage(string)` invokes a registered extension action.

## Pages

Pages are native full-screen destinations and automatically appear in the
conversation drawer:

```ts
aether.registerPage({
  id: "dashboard",
  title: "Build dashboard",
  subtitle: "Extension-owned UI",
  icon: "code",
  render: () => ui.column([
    ui.text("Dashboard", { style: "headline" }),
    ui.button("Run", "run"),
  ]),
});
```

## Service Registry

Services are discoverable and replaceable. The active implementation for an ID
is the highest-priority registration; removing it reveals the next
implementation. Aether core services use a deliberately low priority so a
Native Mod can replace them with the default Native Mod priority.

```ts
const catalog = await aether.services.list();
const skillsApi = await aether.services.describe("skills");
const skills = await aether.services.invoke("skills", "list");
```

Built-in services:

### `skills`

- `list`
- `getSelection`
- `setSelection({ ids, scope, session_id? })`
- `setSelected({ skill_id, selected, scope, session_id? })`

Scopes are `current`, `default`/`global`, or `both`/`current_and_default`.
`both` updates current and default selections independently.

### `state`

- `get({ path })`
- `transaction({ operations })`

## Public state transactions

Supported paths currently include:

- `draft_input`
- `selected_skill_ids`
- `default_skill_ids`
- `agent_mode_enabled`
- `selected_model_key`
- `screen`

```ts
await aether.state.transaction([
  { path: "draft_input", value: "Review the current changes." },
  { path: "agent_mode_enabled", value: true },
]);
```

Transactions apply operations in order. They provide a common mutation API,
not database-level rollback across unrelated Android repositories.

## Operation interception

Script Mods register with `aether.intercept()`. Native interceptors and Script
interceptors form one chain; Native interceptors run first, followed by Script
handlers in extension registration order.

Built-in operations:

- `chat.new`
- `skills.selection`

```ts
aether.intercept("chat.new", ({ selected_skill_ids }) => ({
  selected_skill_ids,
}));

aether.intercept("skills.selection", ({ skill_id, selected }) => {
  if (skill_id === "locked-skill" && !selected) {
    return { cancel: true, reason: "This Skill is locked by the Mod." };
  }
});
```

Returning top-level fields merges them into the chained payload. Returning
`{ payload: {...} }` explicitly supplies payload fields. Returning
`{ cancel: true, reason }` stops the operation.

## Native Kotlin/DEX Mods

Native Mods implement:

```kotlin
interface AetherNativeMod {
    fun onLoad(context: AetherNativeModContext)
    fun onUnload()
}
```

The context intentionally exposes:

- the Android `Application`;
- the package root and Mod classloader;
- the complete `AetherModKernel`;
- convenience registration methods for services, operation interceptors, and
  native Compose components;
- Aether's diagnostic logger.

This is the unrestricted tier. A Native Mod may ignore the convenience APIs
and directly use Android APIs, reflection, JNI, or Aether implementation
classes.

### Native service replacement

```kotlin
class MyNativeMod : AetherNativeMod {
    override fun onLoad(context: AetherNativeModContext) {
        context.registerService(
            id = "skills",
            priority = 500,
            methods = listOf(AetherModServiceMethod("list")),
        ) { method, args ->
            JSONObject().put("provided_by", context.modId)
        }
    }
}
```

### Native operation interceptor

```kotlin
context.intercept("chat.new", priority = 500) { payload, _ ->
    AetherModOperationDecision(
        payload = payload.put("selected_skill_ids", JSONArray()),
    )
}
```

Use `"*"` to observe/intercept every operation exposed through the registry.

### Native Compose replacement

Native component renderers are compiled with the Compose compiler and receive
the complete `AetherUiState`, a JSON public-state snapshot, an async host
bridge, and a `next` composable:

```kotlin
context.registerComponent(
    target = "chat.composer.actionTray",
    id = "native-tray",
    mode = AetherNativeComponentMode.Replace,
    priority = 500,
    renderer = object : AetherNativeComponentRenderer {
        @Composable
        override fun render(
            context: AetherNativeComponentContext,
            next: @Composable () -> Unit,
        ) {
            Text("Rendered from a dynamically loaded Native Mod")
        }
    },
)
```

The Native modes have the same `Before`, `After`, `Replace`, `Wrap`, and `Hide`
semantics as Script components. Native components surround the Script
component pipeline, so a Native replacement can decisively replace both core
and Script-provided content.

### Classpath format

`DexClassLoader` accepts `.dex`, `.apk`, or jar/zip files containing
`classes.dex`. A plain JVM `.class` jar is not sufficient; run Android D8/R8 on
the compiled output. Compose renderers must be compiled with a Compose compiler
compatible with the Aether build they target.

Native Mods currently use an intentionally source/ABI-sensitive API. They
should be rebuilt when Aether implementation classes or the Compose/Kotlin
toolchain changes. This is comparable to version-specific Minecraft Mods.

## Native Mod Safe Mode

Before loading any Native Mod, Aether arms a persistent startup marker. It is
cleared only after Mod loading finishes and the first Activity remains stable
for several seconds.

If the process ends while that marker is armed, the next start:

- skips all Native Mods;
- keeps Script/Pi extensions enabled;
- records the last entrypoint being loaded when available;
- shows Native Mod Safe Mode on the Extensions page.

The user can re-enable Native Mods for the next startup from that page. A
manual “start in Safe Mode next time” action is also available. Safe Mode is a
crash-loop recovery mechanism, not a security sandbox.

## Issue #27 example

[`examples/global-skills-mod`](../examples/global-skills-mod) demonstrates that
the requested global/collapsible Skill workflow can be implemented entirely as
a Script Mod:

- replaces `chat.composer.actionTray`;
- hides `chat.composer.skillPicker`;
- reads and writes Aether's real native Skill selection service;
- persists defaults for every new chat;
- adds a full Global Skills page.

This is also the reference example for combining declarative UI replacement
with native application state instead of maintaining a parallel extension-only
state.

## Existing host methods

The lower-level `aether.host.invoke()` bridge remains available:

| Method | Purpose |
| --- | --- |
| `app.getState` | Read settings, sessions, draft, Skills, and runtime UI state |
| `app.setDraftInput` | Replace composer text |
| `app.appendDraftInput` | Append composer text |
| `app.sendMessage` | Set optional text and submit, queue, or steer |
| `app.newChat` | Start a draft conversation |
| `app.selectSession` | Select a session by ID |
| `app.openScreen` | Open `chat` or `settings` |
| `app.pauseGeneration` | Pause the current generation |
| `app.setReasoningEffort` | Change reasoning effort |
| `app.setAgentMode` | Toggle Agent Mode |
| `app.setModel` | Select a model key |
| `app.notify` | Show an Android toast |
| `settings.get` | Read settings and providers |
| `settings.patch` | Patch supported settings |
| `runtime.execute` | Execute an arbitrary Alpine/Termux command |

Prefer discoverable services and state transactions for reusable Mods; use host
methods for app-specific operations that do not yet have a service.
