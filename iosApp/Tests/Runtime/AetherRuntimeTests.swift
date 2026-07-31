import XCTest
import AetherShared
@testable import Aether

final class AetherRuntimeTests: XCTestCase {
    private let host = AetherRuntimeHost.shared

    override func setUpWithError() throws {
        continueAfterFailure = false
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
