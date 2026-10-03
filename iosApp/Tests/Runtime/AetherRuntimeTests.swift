import XCTest
import AetherShared
@testable import Aether

final class AetherRuntimeTests: XCTestCase {
    func testProviderModelRefreshPreservesDisabledModelsAndRemovesStaleModels() {
        var draft = NativeProviderDraft(
            id: "test", providerId: "openai", name: "OpenAI", piProviderId: "openai",
            authMethod: "api_key", apiKey: "", oauthCredentialJson: "", environment: [],
            baseURL: "", modelIDs: " custom \ncustom\n", userAgent: "", headers: [],
            compatibilityMode: false, cachedModels: ["disabled", "removed"],
            enabledModels: ["removed", "custom"], isEnabled: true, createdAt: 0
        )
        draft.applyFetchedModels([" disabled ", "new", "new", ""])
        XCTAssertEqual(draft.cachedModels, ["disabled", "new"])
        XCTAssertEqual(draft.manualModels, ["custom"])
        XCTAssertEqual(draft.enabledModels, ["custom", "new"])
        draft.enabledModels.remove("new")
        draft.applyFetchedModels(["disabled", "new"])
        XCTAssertEqual(draft.enabledModels, ["custom"])
        draft.applyFetchedModels([], error: "Network unavailable")
        XCTAssertEqual(draft.allModels, ["custom", "disabled", "new"])
        XCTAssertEqual(draft.enabledModels, ["custom"])
        draft.applyFetchedModels([])
        XCTAssertEqual(draft.allModels, ["custom"])
        XCTAssertEqual(draft.enabledModels, ["custom"])
    }

    func testProviderIDGenerationAvoidsExistingConfigurations() {
        XCTAssertEqual(NativeProviderDraft.availableProviderID("openai", existing: []), "openai")
        XCTAssertEqual(NativeProviderDraft.availableProviderID("openai", existing: ["openai", "openai_2"]), "openai_3")
    }

    private let host = AetherRuntimeHost.shared

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    @MainActor
    func testNativeSettingsUsesPersianTranslations() {
        let model = NativeSettingsModel()
        model.snapshot = ["settings": ["language": "fa"]]

        XCTAssertEqual(model.text("Settings", "设置"), "تنظیمات")
        XCTAssertEqual(model.text("Model Providers", "模型提供商"), "ارائه‌دهندگان مدل")
        XCTAssertEqual(model.text("2 installed", "已安装 2 个", "2 نصب‌شده"), "2 نصب‌شده")
    }

    func testInternetPermissionProbeUsesFiniteHeadRequest() throws {
        let request = makeInternetPermissionRequest()

        XCTAssertEqual(request.url?.absoluteString, "https://models.dev/catalog.json")
        XCTAssertEqual(request.httpMethod, "HEAD")
        XCTAssertEqual(request.cachePolicy, .reloadIgnoringLocalCacheData)
        XCTAssertEqual(request.timeoutInterval, 5)
    }

    func testTerminalEmulatorTracksCursorAndWideCharacters() {
        let terminal = TerminalEmulator(cols: 10, rows: 4)

        terminal.feed(Data("\u{1b}[3;5H界".utf8))

        XCTAssertEqual(terminal.activeBuffer.cursorRow, 2)
        XCTAssertEqual(terminal.activeBuffer.cursorCol, 6)
        XCTAssertEqual(terminal.activeBuffer.grid[2][4].character, "界")
        XCTAssertEqual(terminal.activeBuffer.grid[2][4].width, 2)
        XCTAssertTrue(terminal.activeBuffer.grid[2][5].isWideTrailer)
    }

    func testTerminalEmulatorRespondsToCursorPositionReport() {
        let terminal = TerminalEmulator(cols: 10, rows: 4)
        var response: Data?
        terminal.onResponse = { response = $0 }

        terminal.feed(Data("\u{1b}[3;5H\u{1b}[6n".utf8))

        XCTAssertEqual(response, Data("\u{1b}[3;5R".utf8))
    }

    func testTerminalEmulatorRestoresPrimaryBufferAfterAlternateScreen() {
        let terminal = TerminalEmulator(cols: 10, rows: 4)
        terminal.feed(Data("primary".utf8))

        terminal.feed(Data("\u{1b}[?1049halt".utf8))
        XCTAssertTrue(terminal.isAlternateActive)
        XCTAssertEqual(terminal.activeBuffer.grid[0][0].character, "a")

        terminal.feed(Data("\u{1b}[?1049l".utf8))
        XCTAssertFalse(terminal.isAlternateActive)
        XCTAssertEqual(terminal.activeBuffer.grid[0][0].character, "p")
        XCTAssertEqual(terminal.activeBuffer.cursorCol, 7)
    }

    func testTerminalDefaultColorsFollowTheme() {
        XCTAssertEqual(TerminalPalette.defaultBackground(darkTheme: false), .white)
        XCTAssertEqual(TerminalPalette.defaultForeground(darkTheme: false), .black)
        XCTAssertEqual(TerminalPalette.defaultBackground(darkTheme: true), .black)
        XCTAssertNotEqual(TerminalPalette.defaultForeground(darkTheme: true), .black)
    }

    func testNodeAndNpmAreReadyAfterRuntimeInitialization() throws {
        try initializeRuntime()

        let result = try run(
            "/bin/sh",
            arguments: ["-c", "node --version && npm --version"]
        )

        XCTAssertEqual(result.exitCode, 0, result.stderr)
        XCTAssertTrue(result.stdout.contains("v22."), result.stdout)
    }

    func testRuntimeReportsReadyAfterInitializationCompletes() throws {
        try initializeRuntime()

        let checked = expectation(description: "Alpine runtime readiness checked")
        let listener = BooleanCapture(expectation: checked)
        host.isRuntimeReady(listener: listener)
        wait(for: [checked], timeout: 10)

        if let error = listener.error { throw RuntimeTestError.failed(error) }
        XCTAssertEqual(listener.value, true)
    }

    func testAlpinePackageDatabaseLockCanBeAcquired() throws {
        try initializeRuntime()

        let result = try run(
            "/usr/bin/flock",
            arguments: ["-n", "/lib/apk/db/lock", "/bin/true"]
        )

        XCTAssertEqual(result.exitCode, 0, result.stderr)
    }

    func testAlpineProfilePackageCanBeInstalled() throws {
        try initializeRuntime()

        let result = try run(
            "/bin/sh",
            arguments: [
                "-lc",
                "apk add --no-cache --no-chown openssh-client && ssh -V",
            ],
            timeout: 300
        )

        XCTAssertEqual(result.exitCode, 0, result.stdout + result.stderr)
    }

    func testAlpineFileManagerListsMovesAndRemovesGuestEntries() throws {
        try initializeRuntime()
        let directory = "/tmp/aether-file-manager-\(UUID().uuidString)"
        let original = "\(directory)/original.txt"
        let renamed = "\(directory)/renamed.txt"

        let created = expectation(description: "file manager directory created")
        var operationError: Error?
        host.createGuestDirectory(path: directory) { result in
            if case let .failure(error) = result { operationError = error }
            created.fulfill()
        }
        wait(for: [created], timeout: 10)
        if let operationError { throw operationError }
        try writeGuestFile(original, contents: "alpine files")

        let listed = expectation(description: "file manager directory listed")
        var entries: [AlpineFileEntry] = []
        host.listGuestDirectory(path: directory) { result in
            switch result {
            case let .success(value): entries = value
            case let .failure(error): operationError = error
            }
            listed.fulfill()
        }
        wait(for: [listed], timeout: 10)
        if let operationError { throw operationError }
        let entry = try XCTUnwrap(entries.first { $0.name == "original.txt" })
        XCTAssertEqual(entry.path, original)
        XCTAssertFalse(entry.isDirectory)
        XCTAssertEqual(entry.size, 12)

        let moved = expectation(description: "file manager entry moved")
        host.moveGuestPath(from: original, to: renamed) { result in
            if case let .failure(error) = result { operationError = error }
            moved.fulfill()
        }
        wait(for: [moved], timeout: 10)
        if let operationError { throw operationError }

        let removed = expectation(description: "file manager directory removed")
        host.removeGuestPath(path: directory, recursive: true) { result in
            if case let .failure(error) = result { operationError = error }
            removed.fulfill()
        }
        wait(for: [removed], timeout: 10)
        if let operationError { throw operationError }
    }

    func testAlpineFileManagerImportsFilesAndFoldersWithoutOverwriting() throws {
        try initializeRuntime()
        let sourceRoot = FileManager.default.temporaryDirectory
            .appendingPathComponent("aether-import-\(UUID().uuidString)", isDirectory: true)
        let sourceFile = sourceRoot.appendingPathComponent("standalone.txt")
        let sourceFolder = sourceRoot.appendingPathComponent("project", isDirectory: true)
        let nestedFolder = sourceFolder.appendingPathComponent("nested", isDirectory: true)
        let nestedFile = nestedFolder.appendingPathComponent("data.json")
        try FileManager.default.createDirectory(at: nestedFolder, withIntermediateDirectories: true)
        try Data("standalone".utf8).write(to: sourceFile)
        try Data("{\"imported\":true}".utf8).write(to: nestedFile)
        defer { try? FileManager.default.removeItem(at: sourceRoot) }

        let destination = "/tmp/aether-import-destination-\(UUID().uuidString)"
        let created = expectation(description: "import destination created")
        var operationError: Error?
        host.createGuestDirectory(path: destination) { result in
            if case let .failure(error) = result { operationError = error }
            created.fulfill()
        }
        wait(for: [created], timeout: 10)
        if let operationError { throw operationError }

        let imported = expectation(description: "files and folders imported")
        host.importGuestItems(
            urls: [sourceFile, sourceFolder],
            destinationDirectory: destination
        ) { result in
            if case let .failure(error) = result { operationError = error }
            imported.fulfill()
        }
        wait(for: [imported], timeout: 10)
        if let operationError { throw operationError }

        let read = expectation(description: "nested imported file read")
        var importedData: Data?
        host.readGuestFile(path: "\(destination)/project/nested/data.json") { result in
            switch result {
            case let .success(data): importedData = data
            case let .failure(error): operationError = error
            }
            read.fulfill()
        }
        wait(for: [read], timeout: 10)
        if let operationError { throw operationError }
        XCTAssertEqual(String(data: try XCTUnwrap(importedData), encoding: .utf8), "{\"imported\":true}")

        let rejected = expectation(description: "existing item rejected")
        host.importGuestItems(urls: [sourceFile], destinationDirectory: destination) { result in
            if case .failure = result { operationError = nil } else {
                operationError = RuntimeTestError.failed("Import unexpectedly overwrote an existing file.")
            }
            rejected.fulfill()
        }
        wait(for: [rejected], timeout: 10)
        if let operationError { throw operationError }

        let removed = expectation(description: "import destination removed")
        host.removeGuestPath(path: destination, recursive: true) { _ in removed.fulfill() }
        wait(for: [removed], timeout: 10)
    }

    func testAlpineFileManagerExportsBinaryFilesAndSymbolicLinkTargets() throws {
        try initializeRuntime()
        let directory = "/tmp/aether-export-\(UUID().uuidString)"
        let binaryPath = "\(directory)/payload.bin"
        let linkPath = "\(directory)/payload-link"
        let expected = Data([0x00, 0x01, 0x7f, 0x80, 0xfe, 0xff])

        let created = expectation(description: "export fixture directory created")
        let createListener = UnitCapture(expectation: created)
        host.createDirectories(path: directory, listener: createListener)
        wait(for: [created], timeout: 10)
        if let error = createListener.error { throw RuntimeTestError.failed(error) }

        let written = expectation(description: "binary export fixture written")
        let writeListener = UnitCapture(expectation: written)
        host.writeFile(
            path: binaryPath,
            bytes: expected.kotlinByteArray,
            executable: false,
            listener: writeListener
        )
        wait(for: [written], timeout: 10)
        if let error = writeListener.error { throw RuntimeTestError.failed(error) }

        let linked = try run("/bin/ln", arguments: ["-s", binaryPath, linkPath])
        XCTAssertEqual(linked.exitCode, 0, linked.stderr)
        defer { _ = try? run("/bin/rm", arguments: ["-rf", directory]) }

        for guestPath in [binaryPath, linkPath] {
            let destination = FileManager.default.temporaryDirectory
                .appendingPathComponent("aether-export-test-\(UUID().uuidString)")
            defer { try? FileManager.default.removeItem(at: destination) }
            try AetherISHRuntime.shared().exportFile(guestPath, to: destination)
            XCTAssertEqual(try Data(contentsOf: destination), expected)
        }
    }

    func testRetryResetAllowsMountedRuntimeToInitializeAgain() throws {
        try initializeRuntime()

        let reset = expectation(description: "Alpine retry reset completes")
        let listener = UnitCapture(expectation: reset)
        host.resetRuntimeForRetry(listener: listener)
        wait(for: [reset], timeout: 10)
        if let error = listener.error {
            throw RuntimeTestError.failed(error)
        }

        try initializeRuntime()
        let result = try run(
            "/bin/sh",
            arguments: ["-c", "node --version && test -f /root/.aether/pi-bridge/bridge.mjs"]
        )
        XCTAssertEqual(result.exitCode, 0, result.stderr)
    }

    func testLastWorkerThreadReportsTheLeaderProcessExit() throws {
        try initializeRuntime()

        let source = #"""
        #include <pthread.h>
        #include <unistd.h>

        static void *finish_last(void *unused) {
            (void) unused;
            usleep(100000);
            return NULL;
        }

        int main(void) {
            pthread_t worker;
            if (pthread_create(&worker, NULL, finish_last, NULL) != 0) return 2;
            pthread_exit(NULL);
        }
        """#
        try writeGuestFile("/workspace/e2e/last-thread-exit.c", contents: source)
        let compiled = try run(
            "/bin/sh",
            arguments: [
                "-c",
                "command -v cc >/dev/null 2>&1 || " +
                    "apk add --no-cache build-base >/tmp/aether-build-base.log 2>&1; " +
                    "cc -pthread /workspace/e2e/last-thread-exit.c -o /tmp/last-thread-exit",
            ],
            timeout: 300
        )
        XCTAssertEqual(compiled.exitCode, 0, compiled.stderr)

        let result = try run(
            "/tmp/last-thread-exit",
            arguments: [],
            timeout: 30
        )

        XCTAssertEqual(result.exitCode, 0, result.stderr)
    }

    func testProcessExitDoesNotWaitForDescendantHoldingOutputPipe() throws {
        try initializeRuntime()

        let source = #"""
        #include <sys/types.h>
        #include <unistd.h>

        int main(void) {
            pid_t child = fork();
            if (child < 0) return 2;
            if (child == 0) {
                for (int i = 0; i < 5000; i++) {
                    (void) write(STDOUT_FILENO, ".", 1);
                    usleep(1000);
                }
                _exit(0);
            }
            return 0;
        }
        """#
        try writeGuestFile("/workspace/e2e/inherited-output.c", contents: source)
        let compiled = try run(
            "/bin/sh",
            arguments: [
                "-c",
                "command -v cc >/dev/null 2>&1 || " +
                    "apk add --no-cache build-base >/tmp/aether-build-base.log 2>&1; " +
                    "cc /workspace/e2e/inherited-output.c -o /tmp/inherited-output",
            ],
            timeout: 300
        )
        XCTAssertEqual(compiled.exitCode, 0, compiled.stderr)

        let startedAt = Date()
        let result = try run("/tmp/inherited-output", arguments: [], timeout: 3)

        XCTAssertEqual(result.exitCode, 0, result.stderr)
        XCTAssertLessThan(Date().timeIntervalSince(startedAt), 2.5)
    }

    func testUnixSocketBindAndConnect() throws {
        try initializeRuntime()
        let script = #"""
        const net = require('node:net');
        const fs = require('node:fs');
        const { spawn } = require('node:child_process');
        const path = '/tmp/aether-socket-regression-' + process.pid + '-' + Date.now();
        const timer = setTimeout(() => process.exit(2), 15000);
        const server = net.createServer(socket => socket.end('socket-ok'));
        server.on('error', error => { console.error(error); process.exit(1); });
        server.listen(path, () => {
            // iSH connect waits for accept, so the peers need separate guest threads.
            const client = spawn(process.execPath, ['-e', `
                const socket = require('node:net').createConnection(process.argv[1]);
                let output = '';
                socket.on('error', error => { console.error(error); process.exit(1); });
                socket.on('data', data => output += data);
                socket.on('end', () => {
                    console.log(output);
                    process.exitCode = output === 'socket-ok' ? 0 : 3;
                });
            `, path], { stdio: 'inherit' });
            client.on('error', error => { console.error(error); process.exit(1); });
            client.on('exit', code => server.close(() => {
                clearTimeout(timer);
                try { fs.unlinkSync(path); } catch (error) { if (error.code !== 'ENOENT') throw error; }
                process.exitCode = code === 0 ? 0 : 3;
            }));
        });
        """#
        let result = try run("/usr/bin/node", arguments: ["-e", script], timeout: 60)
        XCTAssertEqual(result.exitCode, 0, result.stderr)
        XCTAssertTrue(result.stdout.contains("socket-ok"), result.stdout)
    }

    func testAlpineNodeBridgeTerminalCancellationAndStdioMcp() throws {
        try initializeRuntime()

        let environment = try run(
            "/bin/sh",
            arguments: [
                "-c",
                "printf 'alpine='; cat /etc/alpine-release; " +
                    "printf 'node='; node --version; " +
                    "printf 'arch='; uname -m; " +
                    "mkdir -p /workspace/e2e; " +
                    "printf 'bind-mounted' > /workspace/e2e/probe.txt; " +
                    "printf 'workspace='; cat /workspace/e2e/probe.txt",
            ]
        )
        XCTAssertEqual(environment.exitCode, 0, environment.stderr)
        XCTAssertTrue(environment.stdout.contains("alpine=3.21"), environment.stdout)
        XCTAssertTrue(environment.stdout.contains("node=v22."), environment.stdout)
        XCTAssertTrue(environment.stdout.contains("arch=aarch64"), environment.stdout)
        XCTAssertTrue(environment.stdout.contains("workspace=bind-mounted"), environment.stdout)

        let stdin = try run(
            "/bin/sh",
            arguments: ["-c", "IFS= read -r line; printf 'stdin=%s' \"$line\""],
            stdin: Data("terminal-input\n".utf8),
            interactiveTerminal: true
        )
        XCTAssertEqual(stdin.exitCode, 0, stdin.stderr)
        XCTAssertTrue(stdin.stdout.contains("stdin=terminal-input"), stdin.stdout)

        let cancellation = expectation(description: "cancelled process exits")
        let cancelled = ProcessCapture(exitExpectation: cancellation)
        let cancelledPid = host.startProcess(
            executable: "/bin/sh",
            arguments: ["-c", "sleep 30"],
            environment: [:],
            workingDirectory: "/root",
            redirectErrorStream: false,
            interactiveTerminal: false,
            remoteDebuggingPipe: false,
            listener: cancelled
        )
        XCTAssertGreaterThanOrEqual(cancelledPid, 0)
        host.signal(processId: cancelledPid, signal: 2)
        wait(for: [cancellation], timeout: 10)
        XCTAssertNotEqual(cancelled.exitCode, 0)

        let bridge = try run(
            "/usr/bin/node",
            arguments: ["/root/.aether/pi-bridge/bridge.mjs"],
            stdin: Data("{\"id\":\"ios-e2e\",\"type\":\"ping\",\"payload\":{}}\n".utf8),
            timeout: 30
        )
        XCTAssertEqual(bridge.exitCode, 0, bridge.stderr)
        XCTAssertTrue(bridge.stdout.contains("\"id\":\"ios-e2e\""), bridge.stdout)
        XCTAssertTrue(bridge.stdout.contains("\"bridge_version\":\"2.0.0-alpha.0\""), bridge.stdout)
        XCTAssertTrue(bridge.stdout.contains("\"node_version\":\"v22."), bridge.stdout)

        let mcpServer = #"""
        import { createInterface } from 'node:readline';
        const rl = createInterface({ input: process.stdin });
        rl.on('line', line => {
          const message = JSON.parse(line);
          if (message.method === 'initialize') {
            console.log(JSON.stringify({jsonrpc:'2.0',id:message.id,result:{protocolVersion:'2025-03-26',capabilities:{tools:{}},serverInfo:{name:'ios-e2e',version:'1'}}}));
          } else if (message.method === 'tools/list') {
            console.log(JSON.stringify({jsonrpc:'2.0',id:message.id,result:{tools:[{name:'echo',description:'Echo',inputSchema:{type:'object'}}]}}));
          } else if (message.method === 'tools/call') {
            console.log(JSON.stringify({jsonrpc:'2.0',id:message.id,result:{content:[{type:'text',text:'mcp-ok'}]}}));
          }
        });
        """#
        try writeGuestFile("/workspace/e2e/mcp-server.mjs", contents: mcpServer)
        let mcp = try run(
            "/usr/bin/node",
            arguments: ["/workspace/e2e/mcp-server.mjs"],
            stdin: Data(
                ("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}\n" +
                 "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}\n" +
                 "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}\n" +
                 "{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"echo\",\"arguments\":{}}}\n").utf8
            )
        )
        XCTAssertEqual(mcp.exitCode, 0, mcp.stderr)
        XCTAssertTrue(mcp.stdout.contains("\"name\":\"echo\""), mcp.stdout)
        XCTAssertTrue(mcp.stdout.contains("mcp-ok"), mcp.stdout)

        let http = try run(
            "/usr/bin/node",
            arguments: [
                "-e",
                "const http=require('node:http');" +
                    "const s=http.createServer((q,r)=>{r.setHeader('content-type','application/json');" +
                    "r.end(JSON.stringify({jsonrpc:'2.0',id:1,result:{content:[{type:'text',text:'http-mcp-ok'}]}}))});" +
                    "s.listen(17890,'127.0.0.1',async()=>{const v=await (await fetch('http://127.0.0.1:17890/mcp',{method:'POST',body:'{}'})).text();" +
                    "console.log(v);s.close()})",
            ]
        )
        XCTAssertEqual(http.exitCode, 0, http.stderr)
        XCTAssertTrue(http.stdout.contains("http-mcp-ok"), http.stdout)

        let crossProcess = try run(
            "/bin/sh",
            arguments: [
                "-c",
                "node -e \"require('node:http').createServer((q,r)=>r.end('cross-ok')).listen(17891,'127.0.0.1')\" >/tmp/cross-http.log 2>&1 & " +
                    "server=$!; code=1; " +
                    "for attempt in $(seq 1 30); do " +
                    "if output=$(wget -qO- http://127.0.0.1:17891 2>/dev/null); then printf '%s' \"$output\"; code=0; break; fi; " +
                    "sleep 1; done; " +
                    "if [ $code -ne 0 ]; then cat /tmp/cross-http.log >&2; fi; " +
                    "kill $server >/dev/null 2>&1 || true; exit $code",
            ]
        )
        XCTAssertEqual(crossProcess.exitCode, 0, crossProcess.stderr)
        XCTAssertTrue(crossProcess.stdout.contains("cross-ok"), crossProcess.stdout)

        try writeGuestFile(
            "/workspace/e2e/aether-ios-npm/package.json",
            contents: #"{"name":"aether-ios-e2e","version":"1.0.0","description":"iOS npm extension E2E","aether":{"api":2,"extensions":["./index.mjs"]}}"#
        )
        try writeGuestFile(
            "/workspace/e2e/aether-ios-npm/index.mjs",
            contents: #"export default function(aether){aether.registerSurface('chat.composer.top',{tree:{type:'text',text:'ios-npm-extension-ok'}})}"#
        )
        let npmExtension = try runUntilStdoutContains(
            "/usr/bin/node",
            arguments: ["/root/.aether/pi-bridge/bridge.mjs"],
            stdin: Data(
                "{\"id\":\"npm-ios-e2e\",\"type\":\"install_extension_package\",\"payload\":{\"source\":\"npm:aether-ios-e2e@file:/workspace/e2e/aether-ios-npm\"}}\n".utf8
            ),
            marker: "\"id\":\"npm-ios-e2e\"",
            timeout: 180
        )
        XCTAssertTrue(npmExtension.stdout.contains("\"installed\":true"), npmExtension.stdout)
        XCTAssertTrue(npmExtension.stdout.contains("aether-ios-e2e"), npmExtension.stdout)
        XCTAssertTrue(npmExtension.stdout.contains("ios-npm-extension-ok"), npmExtension.stdout)
    }

    func testChromiumNoVncAndCdp() throws {
        try XCTSkipIf(true, "iOS Chrome support is deferred.")

        try initializeRuntime()

        let manager = SharedChromeManager(runtime: IosAlpineRuntime(host: host))
        manager.enabled = true
        let started = expectation(description: "shared Chrome manager starts")
        var startError: Error?
        manager.start { _, error in
            startError = error
            started.fulfill()
        }
        wait(for: [started], timeout: 900)
        if let startError { throw startError }

        let chrome = try run(
            "/bin/sh",
            arguments: [
                "-c",
                "chromium --version",
            ],
            timeout: 60
        )
        XCTAssertEqual(chrome.exitCode, 0, chrome.stderr)
        XCTAssertTrue(chrome.stdout.contains("Chromium"), chrome.stdout)

        let status = try executeChrome(manager, request: #"{"action":"status"}"#)
        XCTAssertEqual(status["started"] as? Bool, true)
        _ = try executeChrome(
            manager,
            request: #"{"action":"navigate","url":"data:text/html,<button id='probe' onclick='this.textContent=\"clicked\"'>ready</button>"}"#
        )
        Thread.sleep(forTimeInterval: 0.5)
        let before = try executeChrome(
            manager,
            request: #"{"action":"evaluate","expression":"document.getElementById('probe').textContent"}"#
        )
        XCTAssertEqual(((before["result"] as? [String: Any])?["value"] as? String), "ready")
        _ = try executeChrome(
            manager,
            request: #"{"action":"evaluate","expression":"document.getElementById('probe').click()"}"#
        )
        let after = try executeChrome(
            manager,
            request: #"{"action":"evaluate","expression":"document.getElementById('probe').textContent"}"#
        )
        XCTAssertEqual(((after["result"] as? [String: Any])?["value"] as? String), "clicked")
        let shot = try executeChrome(
            manager,
            request: #"{"action":"screenshot","path":"/workspace/e2e/chrome-cdp.png"}"#
        )
        XCTAssertGreaterThan(shot["size"] as? Int ?? 0, 1_000)

        let screenshot = try Data(
            contentsOf: FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
                .appendingPathComponent("Workspace/e2e/chrome-cdp.png")
        )
        XCTAssertGreaterThan(screenshot.count, 1_000)
        XCTAssertEqual(Array(screenshot.prefix(8)), [137, 80, 78, 71, 13, 10, 26, 10])

        let noVncLoaded = expectation(description: "noVNC viewer responds to iOS host")
        var noVncData = Data()
        var noVncError: Error?
        URLSession.shared.dataTask(with: URL(string: manager.viewerUrl)!) { data, _, error in
            noVncData = data ?? Data()
            noVncError = error
            noVncLoaded.fulfill()
        }.resume()
        wait(for: [noVncLoaded], timeout: 30)
        XCTAssertNil(noVncError)
        XCTAssertTrue(String(decoding: noVncData, as: UTF8.self).contains("noVNC"))

        let stopped = expectation(description: "shared Chrome manager stops")
        manager.stop { _ in stopped.fulfill() }
        wait(for: [stopped], timeout: 30)
    }

    func testNativeRemoteDebuggingPipeTransport() throws {
        try initializeRuntime()

        let exited = expectation(description: "fd 3/4 probe exits")
        let listener = ProcessCapture(exitExpectation: exited)
        let pid = host.startProcess(
            executable: "/bin/sh",
            arguments: ["-c", "IFS= read -r line <&3; printf 'pipe=%s' \"$line\" >&4"],
            environment: [:],
            workingDirectory: "/root",
            redirectErrorStream: false,
            interactiveTerminal: false,
            remoteDebuggingPipe: true,
            listener: listener
        )
        XCTAssertGreaterThanOrEqual(pid, 0)
        XCTAssertTrue(host.writeStdin(processId: pid, bytes: Data("probe\n".utf8).kotlinByteArray))
        host.closeStdin(processId: pid)
        wait(for: [exited], timeout: 30)
        XCTAssertEqual(listener.result.exitCode, 0, listener.result.stderr)
        XCTAssertEqual(listener.result.stdout, "pipe=probe")
    }

    func testMultithreadedForkKeepsParentMemoryPrivate() throws {
        try initializeRuntime()

        let source = #"""
        #include <pthread.h>
        #include <stdatomic.h>
        #include <stdint.h>
        #include <stdio.h>
        #include <stdlib.h>
        #include <string.h>
        #include <sys/wait.h>
        #include <unistd.h>

        enum { PAGE_COUNT = 16, PAGE_SIZE = 4096 };
        static _Alignas(PAGE_SIZE) unsigned char ownership[PAGE_COUNT * PAGE_SIZE];
        static atomic_int worker_ready;

        static void *worker(void *unused) {
            (void) unused;
            atomic_store_explicit(&worker_ready, 1, memory_order_release);
            for (;;) sched_yield();
        }

        int main(void) {
            memset(ownership, 0xa5, sizeof(ownership));
            pthread_t thread;
            if (pthread_create(&thread, NULL, worker, NULL) != 0) return 10;
            while (!atomic_load_explicit(&worker_ready, memory_order_acquire)) sched_yield();

            int sync_pipe[2];
            if (pipe(sync_pipe) != 0) return 11;
            pid_t child = fork();
            if (child < 0) return 12;
            if (child == 0) {
                close(sync_pipe[0]);
                memset(ownership, 0, sizeof(ownership));
                if (write(sync_pipe[1], "x", 1) != 1) _exit(13);
                _exit(0);
            }

            close(sync_pipe[1]);
            char marker = 0;
            if (read(sync_pipe[0], &marker, 1) != 1 || marker != 'x') return 14;
            for (size_t i = 0; i < sizeof(ownership); ++i) {
                if (ownership[i] != 0xa5) {
                    fprintf(stderr, "parent page changed at byte %zu: 0x%02x\n", i, ownership[i]);
                    return 15;
                }
            }
            int status = 0;
            if (waitpid(child, &status, 0) != child || !WIFEXITED(status) || WEXITSTATUS(status) != 0) return 16;
            puts("fork-cow-ok");
            return 0;
        }
        """#
        try writeGuestFile("/workspace/e2e/fork-cow.c", contents: source)
        let result = try run(
            "/bin/sh",
            arguments: [
                "-c",
                "command -v cc >/dev/null 2>&1 || apk add --no-cache build-base >/tmp/aether-build-base.log 2>&1; " +
                    "cc -std=c11 -O0 -pthread /workspace/e2e/fork-cow.c -o /tmp/fork-cow && /tmp/fork-cow",
            ],
            timeout: 300
        )
        XCTAssertEqual(result.exitCode, 0, result.stderr)
        XCTAssertTrue(result.stdout.contains("fork-cow-ok"), result.stdout)
    }

    private func executeChrome(
        _ manager: SharedChromeManager,
        request: String,
        timeout: TimeInterval = 120
    ) throws -> [String: Any] {
        let completed = expectation(description: "Chrome command completes")
        var output: String?
        var commandError: Error?
        manager.executeJson(requestJson: request) { result, error in
            output = result
            commandError = error
            completed.fulfill()
        }
        wait(for: [completed], timeout: timeout)
        if let commandError { throw commandError }
        guard let output, let data = output.data(using: .utf8) else {
            throw RuntimeTestError.failed("Chrome returned no JSON result.")
        }
        return try XCTUnwrap(JSONSerialization.jsonObject(with: data) as? [String: Any])
    }

    private func initializeRuntime() throws {
        let ready = expectation(description: "Alpine runtime ready")
        let listener = InitializationCapture(expectation: ready)
        host.initialize(listener: listener)
        wait(for: [ready], timeout: 300)
        if let error = listener.error {
            throw RuntimeTestError.failed(error)
        }
    }

    private func run(
        _ executable: String,
        arguments: [String],
        stdin: Data? = nil,
        interactiveTerminal: Bool = false,
        timeout: TimeInterval = 60
    ) throws -> ProcessResult {
        let exited = expectation(description: "\(executable) exits")
        let listener = ProcessCapture(exitExpectation: exited)
        let pid = host.startProcess(
            executable: executable,
            arguments: arguments,
            environment: [:],
            workingDirectory: "/root",
            redirectErrorStream: false,
            interactiveTerminal: interactiveTerminal,
            remoteDebuggingPipe: false,
            listener: listener
        )
        guard pid >= 0 else { throw RuntimeTestError.failed("Unable to start \(executable): \(pid)") }
        if let stdin {
            XCTAssertTrue(host.writeStdin(processId: pid, bytes: stdin.kotlinByteArray))
        }
        host.closeStdin(processId: pid)
        wait(for: [exited], timeout: timeout)
        return listener.result
    }

    private func runUntilStdoutContains(
        _ executable: String,
        arguments: [String],
        stdin: Data,
        marker: String,
        timeout: TimeInterval
    ) throws -> ProcessResult {
        let received = expectation(description: "\(executable) emits \(marker)")
        let exited = expectation(description: "\(executable) exits after response")
        let listener = ProcessCapture(
            exitExpectation: exited,
            stdoutMarker: marker,
            stdoutExpectation: received
        )
        let pid = host.startProcess(
            executable: executable,
            arguments: arguments,
            environment: [:],
            workingDirectory: "/root",
            redirectErrorStream: false,
            interactiveTerminal: false,
            remoteDebuggingPipe: false,
            listener: listener
        )
        guard pid >= 0 else { throw RuntimeTestError.failed("Unable to start \(executable): \(pid)") }
        XCTAssertTrue(host.writeStdin(processId: pid, bytes: stdin.kotlinByteArray))
        host.closeStdin(processId: pid)
        wait(for: [received], timeout: timeout)
        host.signal(processId: pid, signal: 15)
        wait(for: [exited], timeout: 10)
        return listener.result
    }

    private func writeGuestFile(_ path: String, contents: String) throws {
        let parent = String(path.dropLast(path.split(separator: "/").last?.count ?? 0)).dropLast()
        let directoryCompleted = expectation(description: "create parent for \(path)")
        let directoryListener = UnitCapture(expectation: directoryCompleted)
        host.createDirectories(path: String(parent), listener: directoryListener)
        wait(for: [directoryCompleted], timeout: 10)
        if let error = directoryListener.error { throw RuntimeTestError.failed(error) }

        let completed = expectation(description: "write \(path)")
        let listener = UnitCapture(expectation: completed)
        host.writeFile(
            path: path,
            bytes: Data(contents.utf8).kotlinByteArray,
            executable: false,
            listener: listener
        )
        wait(for: [completed], timeout: 10)
        if let error = listener.error { throw RuntimeTestError.failed(error) }
    }
}

private struct ProcessResult {
    let stdout: String
    let stderr: String
    let exitCode: Int32
}

private final class InitializationCapture: NSObject, NativeRuntimeInitializationListener {
    private let expectation: XCTestExpectation
    var error: String?

    init(expectation: XCTestExpectation) { self.expectation = expectation }
    func onProgress(phase: String, detail: String, fraction: Double) {}
    func onOutput(text: String) {}
    func onReady() { expectation.fulfill() }
    func onError(message: String) { error = message; expectation.fulfill() }
}

private final class BooleanCapture: NSObject, NativeBooleanResultListener {
    private let expectation: XCTestExpectation
    var value: Bool?
    var error: String?

    init(expectation: XCTestExpectation) { self.expectation = expectation }
    func onSuccess(value: Bool) { self.value = value; expectation.fulfill() }
    func onError(message: String) { error = message; expectation.fulfill() }
}

private final class ProcessCapture: NSObject, NativeRuntimeProcessListener {
    private let exitExpectation: XCTestExpectation
    private let stdoutMarker: String?
    private let stdoutExpectation: XCTestExpectation?
    private var stdoutData = Data()
    private var stderrData = Data()
    private var stdoutExpectationFulfilled = false
    var exitCode: Int32 = Int32.min

    init(
        exitExpectation: XCTestExpectation,
        stdoutMarker: String? = nil,
        stdoutExpectation: XCTestExpectation? = nil
    ) {
        self.exitExpectation = exitExpectation
        self.stdoutMarker = stdoutMarker
        self.stdoutExpectation = stdoutExpectation
    }
    func onStdout(bytes: KotlinByteArray) {
        stdoutData.append(bytes.data)
        if
            !stdoutExpectationFulfilled,
            let stdoutMarker,
            String(decoding: stdoutData, as: UTF8.self).contains(stdoutMarker)
        {
            stdoutExpectationFulfilled = true
            stdoutExpectation?.fulfill()
        }
    }
    func onStderr(bytes: KotlinByteArray) { stderrData.append(bytes.data) }
    func onExit(exitCode: Int32, signal: Int32) {
        self.exitCode = exitCode
        exitExpectation.fulfill()
    }
    var result: ProcessResult {
        ProcessResult(
            stdout: String(decoding: stdoutData, as: UTF8.self),
            stderr: String(decoding: stderrData, as: UTF8.self),
            exitCode: exitCode
        )
    }
}

private final class UnitCapture: NSObject, NativeUnitResultListener {
    private let expectation: XCTestExpectation
    var error: String?

    init(expectation: XCTestExpectation) { self.expectation = expectation }
    func onSuccess() { expectation.fulfill() }
    func onError(message: String) { error = message; expectation.fulfill() }
}

private enum RuntimeTestError: Error {
    case failed(String)
}

private extension Data {
    var kotlinByteArray: KotlinByteArray {
        let result = KotlinByteArray(size: Int32(count))
        for (index, byte) in enumerated() {
            result.set(index: Int32(index), value: Int8(bitPattern: byte))
        }
        return result
    }
}

private extension KotlinByteArray {
    var data: Data {
        var result = Data(count: Int(size))
        result.withUnsafeMutableBytes { buffer in
            guard let target = buffer.baseAddress?.assumingMemoryBound(to: UInt8.self) else { return }
            for index in 0..<Int(size) {
                target[index] = UInt8(bitPattern: get(index: Int32(index)))
            }
        }
        return result
    }
}

final class ANSIParserOSCTests: XCTestCase {
    private let esc: UInt8 = 0x1B
    private let bel: UInt8 = 0x07

    private func actions(_ bytes: [UInt8], parser: ANSIParser = ANSIParser()) -> [String] {
        var output: [String] = []
        parser.feed(bytes) { action in
            switch action {
            case .printable(let character): output.append("print:\(character)")
            case .controlChar(let byte): output.append("control:\(byte)")
            case .csiDispatch(_, _, let finalByte): output.append("csi:\(finalByte)")
            case .escDispatch(let character): output.append("esc:\(character)")
            case .oscDispatch(let command, let payload): output.append("osc:\(command):\(payload)")
            }
        }
        return output
    }

    private func osc(_ body: String, terminator: [UInt8]) -> [UInt8] {
        [esc] + Array("]\(body)".utf8) + terminator
    }

    func testMultibyteTitlesDecodeAsUTF8() {
        XCTAssertEqual(actions(osc("0;你好世界", terminator: [bel])), ["osc:0:你好世界"])
        XCTAssertEqual(
            actions(osc("2;build ✅ 🚀", terminator: [esc, 0x5C])),
            ["osc:2:build ✅ 🚀", "esc:\\"]
        )
    }

    func testBackslashInsidePayloadIsKept() {
        XCTAssertEqual(actions(osc("0;C:\\Users\\a", terminator: [bel])), ["osc:0:C:\\Users\\a"])
    }

    func testMalformedUTF8InPayloadIsDropped() {
        XCTAssertEqual(actions(osc("0;a", terminator: [0xE4, 0xBD, bel])), ["osc:0:a"])
        XCTAssertEqual(actions([esc] + Array("]0;".utf8) + [0xE4] + Array("b".utf8) + [bel]), ["osc:0:b"])
        XCTAssertEqual(actions([esc] + Array("]0;".utf8) + [0x80] + Array("c".utf8) + [bel]), ["osc:0:c"])
    }

    func testBareBackslashInCommandAbortsWithoutDispatch() {
        // Must not dispatch an empty OSC 0, which would clear the title.
        XCTAssertEqual(actions([esc] + Array("]0\\x".utf8)), ["print:x"])
    }

    func testResetDropsPendingPayloadBytes() {
        let parser = ANSIParser()
        _ = actions([esc] + Array("]0;".utf8) + [0xE4, 0xBD], parser: parser)
        parser.reset()
        // A lone continuation byte must not complete the discarded sequence.
        XCTAssertEqual(
            actions([esc] + Array("]0;".utf8) + [0xA0] + Array("ok".utf8) + [bel], parser: parser),
            ["osc:0:ok"]
        )
    }
}
