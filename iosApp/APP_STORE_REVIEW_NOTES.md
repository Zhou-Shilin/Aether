# App Store Review Notes

## Local runtime

Aether includes an Alpine Linux ARM64 root filesystem and Node.js 22 so that
agent tools can run locally and access only the app sandbox. The Linux system is
executed by the bundled, GPLv3-licensed `ish-arm64` user-mode emulator. It is not
a virtual machine, does not use private iOS APIs, and does not escape the app
sandbox. The host exposes only the app-owned workspace directory to the guest.

The iOS build does not use a native JIT and does not download or generate
Mach-O code, frameworks, or executable host code. Guest ARM64 instructions are
interpreted/emulated by code included in the reviewed binary. JavaScript and
user-installed script extensions are interpreted inside the bundled guest
runtime and can reach iOS services only through the fixed, reviewed Aether host
bridge. Kotlin/DEX native mods are disabled on iOS.

iOS Chrome support is intentionally not exposed in this release. Termux,
runtime selection, Agent Mode, Root/Shizuku, scheduled tasks, persistent
background execution, and native mods are also unavailable on iOS.

## Network use

Network requests are initiated for user-configured AI providers, OAuth login,
optional web search and URL fetching, user-configured HTTP MCP servers, npm
extension installation, Alpine package installation, and App Store version
lookup. Provider credentials remain in the app's private storage and are sent
only to the provider selected by the user. Aether does not track users or sell
data.

## Background execution

Aether requests an ordinary finite iOS background task only when a chat turn is
already running. When iOS expires that task, Aether cancels the guest process,
saves partial output, and marks the turn as retryable. It does not start new
work in the background or claim continuous background execution.

## Open-source source offer

The bundled `ish-arm64` license, iOS exception, exact upstream base commit,
Aether modifications, and corresponding source instructions are documented in
`THIRD_PARTY_NOTICES.md`. Release archives must link to the matching public
Aether source tag containing the complete modified source.
