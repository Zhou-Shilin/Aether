import Foundation
import CryptoKit
import UIKit
import PhotosUI
import UniformTypeIdentifiers
import QuickLook
import Darwin
import BackgroundTasks
import AuthenticationServices
import AetherShared

private let alpineNetworkTraceURL = URL(string: "https://www.cloudflare.com/cdn-cgi/trace")!
private let alpineOfficialRepository = "https://dl-cdn.alpinelinux.org/alpine"
private let alpineChinaRepository = "https://mirrors.tuna.tsinghua.edu.cn/alpine"

private enum AlpineNetworkEnvironment {
    case china
    case international
    case unknown
}

private func cloudflareCountryCode(_ trace: String) -> String? {
    trace.split(separator: "\n").first { line in
        line.lowercased().hasPrefix("loc=")
    }.map { String($0.dropFirst(4)).trimmingCharacters(in: .whitespacesAndNewlines).uppercased() }
}

private func alpineRepositories(
    _ contents: String,
    environment: AlpineNetworkEnvironment
) -> String {
    contents.split(separator: "\n", omittingEmptySubsequences: false).flatMap { line -> [String] in
        let originalLine = String(line)
        let official = originalLine
            .replacingOccurrences(of: "https://dl-cdn.alpinelinux.org/alpine", with: alpineOfficialRepository)
            .replacingOccurrences(of: "http://dl-cdn.alpinelinux.org/alpine", with: alpineOfficialRepository)
            .replacingOccurrences(of: "https://mirrors.tuna.tsinghua.edu.cn/alpine", with: alpineOfficialRepository)
            .replacingOccurrences(of: "http://mirrors.tuna.tsinghua.edu.cn/alpine", with: alpineOfficialRepository)
        let isAlpineRepository = originalLine.contains("dl-cdn.alpinelinux.org/alpine") ||
            originalLine.contains("mirrors.tuna.tsinghua.edu.cn/alpine")
        guard isAlpineRepository else { return [originalLine] }
        let china = official.replacingOccurrences(of: alpineOfficialRepository, with: alpineChinaRepository)
        switch environment {
        case .china:
            return [china, official]
        case .international:
            return [official]
        case .unknown:
            return [official, china]
        }
    }.reduce(into: [String]()) { lines, line in
        if !lines.contains(line) { lines.append(line) }
    }.joined(separator: "\n")
}

final class AetherRuntimeHost: NSObject, NativeRuntimeHost, UIDocumentPickerDelegate, PHPickerViewControllerDelegate, QLPreviewControllerDataSource, ASWebAuthenticationPresentationContextProviding {
    static let shared = AetherRuntimeHost()

    private let runtime = AetherISHRuntime.shared()
    private let operations = DispatchQueue(label: "com.baimoqilin.aether.runtime-host")
    private var initialized = false
    private var initializationInProgress = false
    private var initializationListeners: [NativeRuntimeInitializationListener] = []
    private var filePickerListener: NativePickedFileListener?
    private var filesPickerListener: NativePickedFilesListener?
    private var directoryPickerListener: NativePickedDirectoryListener?
    private var fileExportListener: NativeFileExportListener?
    private var fileExportURL: URL?
    private var guestFileExportCompletion: ((Result<Void, Error>) -> Void)?
    private var previewURL: URL?
    private var authenticationSession: ASWebAuthenticationSession?
    private let backgroundExecution = AetherBackgroundExecutionCoordinator.shared
    private var startupNetworkEnvironment: AlpineNetworkEnvironment?

    private let maximumPickedDirectoryEntries = 4_096
    private let maximumPickedDirectoryEntryBytes = 16 * 1024 * 1024
    private let maximumPickedDirectoryBytes = 128 * 1024 * 1024

    func refreshApkRepositoriesForCurrentNetwork() {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.timeoutIntervalForRequest = 4
        configuration.timeoutIntervalForResource = 4
        let session = URLSession(configuration: configuration)
        var request = URLRequest(
            url: alpineNetworkTraceURL,
            cachePolicy: .reloadIgnoringLocalCacheData,
            timeoutInterval: 4
        )
        request.setValue("text/plain", forHTTPHeaderField: "Accept")
        request.setValue("Aether-Alpine-Network-Check", forHTTPHeaderField: "User-Agent")
        session.dataTask(with: request) { [weak self] data, response, _ in
            let statusCode = (response as? HTTPURLResponse)?.statusCode ?? 0
            let countryCode = data.flatMap { String(data: $0, encoding: .utf8) }.flatMap(cloudflareCountryCode)
            let environment: AlpineNetworkEnvironment
            if (200...299).contains(statusCode), let countryCode {
                environment = countryCode.caseInsensitiveCompare("CN") == .orderedSame ? .china : .international
            } else {
                environment = .unknown
            }
            self?.operations.async {
                guard let self else { return }
                self.startupNetworkEnvironment = environment
                if self.initialized {
                    try? self.configureApkRepositories(environment: environment)
                }
            }
            session.finishTasksAndInvalidate()
        }.resume()
    }

    func isRuntimeReady(listener: NativeBooleanResultListener) {
        operations.async { [self] in
            do {
                try purgePendingAlpineReset()
                let root = try alpineRuntimeRootURL()
                let hasData = FileManager.default.fileExists(
                    atPath: root.appendingPathComponent("data", isDirectory: true).path
                )
                let hasDatabase = FileManager.default.fileExists(
                    atPath: root.appendingPathComponent("meta.db", isDirectory: false).path
                )
                guard hasData && hasDatabase else {
                    onMain { listener.onSuccess(value: false) }
                    return
                }
                if initialized {
                    onMain { listener.onSuccess(value: true) }
                    return
                }

                // The iSH kernel state is process-local. After an app relaunch the
                // rootfs can be complete while the in-memory runtime is not booted;
                // recover it before reporting readiness to shared UI and tools.
                initialize(listener: RuntimeReadinessInitializationListener(
                    onReady: { listener.onSuccess(value: true) },
                    onError: { listener.onError(message: $0) },
                ))
            } catch {
                onMain { listener.onError(message: error.localizedDescription) }
            }
        }
    }

    func beginBackgroundExecution(name: String, listener: NativeBackgroundExecutionListener) -> String {
        backgroundExecution.begin(name: name) { listener.onExpired() }
    }

    func registerBackgroundExecution() {
        backgroundExecution.register()
    }

    func updateBackgroundExecution(identifier: String, detail: String) {
        backgroundExecution.update(identifier: identifier, detail: detail)
    }

    func endBackgroundExecution(identifier: String, success: Bool) {
        backgroundExecution.end(identifier: identifier, success: success)
    }

    func initialize(listener: NativeRuntimeInitializationListener) {
        operations.async { [self] in
            if initialized {
                onMain { listener.onReady() }
                return
            }
            initializationListeners.append(listener)
            guard !initializationInProgress else { return }
            initializationInProgress = true

            do {
                try purgePendingAlpineReset()
            } catch {
                finishInitialization(error: error.localizedDescription)
                return
            }
            reportInitializationProgress(
                phase: "rootfs",
                detail: "Preparing Alpine",
                fraction: 0.02
            )
            runtime.initialize(
                progress: { phase, detail, fraction in
                    self.operations.async {
                        self.reportInitializationProgress(
                            phase: phase,
                            detail: detail,
                            fraction: fraction
                        )
                    }
                },
                completion: { error in
                    self.operations.async {
                        if let error {
                            self.finishInitialization(error: error.localizedDescription)
                        } else {
                            self.prepareInitializedRuntime()
                        }
                    }
                }
            )
        }
    }

    func resetRuntime(listener: NativeUnitResultListener) {
        operations.async { [self] in
            do {
                let root = try alpineRuntimeRootURL()
                let marker = try alpineResetMarkerURL()
                if runtime.isInitialized {
                    try Data().write(to: marker, options: .atomic)
                    complete { listener.onSuccess() }
                } else {
                    if FileManager.default.fileExists(atPath: root.path) {
                        try FileManager.default.removeItem(at: root)
                    }
                    if FileManager.default.fileExists(atPath: marker.path) {
                        try FileManager.default.removeItem(at: marker)
                    }
                    initialized = false
                    complete { listener.onSuccess() }
                }
            } catch {
                complete { listener.onError(message: error.localizedDescription) }
            }
        }
    }

    func resetRuntimeForRetry(listener: NativeUnitResultListener) {
        operations.async { [self] in
            do {
                let marker = try alpineResetMarkerURL()
                if FileManager.default.fileExists(atPath: marker.path) {
                    try purgePendingAlpineReset()
                }

                initialized = false
                guard !runtime.isInitialized else {
                    complete { listener.onSuccess() }
                    return
                }

                let root = try alpineRuntimeRootURL()
                if FileManager.default.fileExists(atPath: root.path) {
                    try FileManager.default.removeItem(at: root)
                }
                complete { listener.onSuccess() }
            } catch {
                complete { listener.onError(message: error.localizedDescription) }
            }
        }
    }

    private func prepareInitializedRuntime() {
        do {
            let workspace = try workspaceURL()
            let chromeRuntime = try chromeRuntimeURL()
            let chromeDependencies = try chromeDependenciesURL()
            try configureApkRepositories(environment: startupNetworkEnvironment ?? .unknown)
            try guestCreateDirectories("/workspace")
            try guestBind(hostPath: workspace.path, guestPath: "/workspace")
            try guestCreateDirectories("/usr/lib/chromium")
            try guestBind(hostPath: chromeRuntime.path, guestPath: "/usr/lib/chromium")
            try guestCreateDirectories("/opt/aether/chromium-deps")
            try guestBind(hostPath: chromeDependencies.path, guestPath: "/opt/aether/chromium-deps")
            try installBridgeAsset()
            try installPreinstalledExtensions()
            try installNodeCompatibilityAssets()
        } catch {
            finishInitialization(error: error.localizedDescription)
            return
        }

        checkNode()
    }

    private func checkNode() {
        reportInitializationProgress(
            phase: "checking_node",
            detail: "Checking Node 22",
            fraction: 0.82
        )
        let pid = runtime.startExecutable(
            "/bin/sh",
            arguments: ["-c", Self.nodeVerificationCommand],
            environment: [:],
            workingDirectory: "/root",
            pseudoTerminal: false,
            remoteDebuggingPipe: false,
            standardOutput: { _ in },
            standardError: { _ in },
            exit: { code, _ in
                self.operations.async {
                    if code == 0 {
                        self.markRuntimeReady()
                    } else {
                        self.installNode()
                    }
                }
            }
        )
        if pid < 0 {
            finishInitialization(error: "Unable to check Node 22 in Alpine (\(pid)).")
        }
    }

    private func installNode() {
        reportInitializationProgress(
            phase: "installing_node",
            detail: "Installing Node 22",
            fraction: 0.84
        )
        var installOutput = ""
        let pid = runtime.startExecutable(
            "/bin/sh",
            arguments: ["-c", Self.nodeInstallCommand],
            environment: [:],
            workingDirectory: "/root",
            pseudoTerminal: false,
            remoteDebuggingPipe: false,
            standardOutput: { data in
                installOutput.append(String(decoding: data, as: UTF8.self))
                self.operations.async { self.reportInitializationOutput(data) }
            },
            standardError: { data in
                installOutput.append(String(decoding: data, as: UTF8.self))
                self.operations.async { self.reportInitializationOutput(data) }
            },
            exit: { code, _ in
                self.operations.async {
                    self.verifyNodeAfterInstall(
                        installExitCode: code,
                        installOutput: installOutput
                    )
                }
            }
        )
        if pid < 0 {
            finishInitialization(error: "Unable to start Alpine package setup (\(pid)).")
        }
    }

    private func verifyNodeAfterInstall(
        installExitCode: Int32,
        installOutput: String
    ) {
        let pid = runtime.startExecutable(
            "/bin/sh",
            arguments: ["-c", Self.nodeVerificationCommand],
            environment: [:],
            workingDirectory: "/root",
            pseudoTerminal: false,
            remoteDebuggingPipe: false,
            standardOutput: { _ in },
            standardError: { _ in },
            exit: { code, _ in
                self.operations.async {
                    if code == 0 {
                        self.markRuntimeReady()
                        return
                    }
                    let detail = installOutput
                        .trimmingCharacters(in: .whitespacesAndNewlines)
                        .suffix(2_000)
                    let suffix = detail.isEmpty ? "" : "\n\(detail)"
                    self.finishInitialization(
                        error: "Unable to install Node 22 in Alpine (apk exit \(installExitCode), verification exit \(code)).\(suffix)"
                    )
                }
            }
        )
        if pid < 0 {
            finishInitialization(error: "Unable to verify Node 22 in Alpine (\(pid)).")
        }
    }

    private static let nodeVerificationCommand =
        "node --version 2>/dev/null | grep -q '^v22\\.' && command -v npm >/dev/null 2>&1"

    private static let nodeInstallCommand = """
        attempt=1
        while [ "$attempt" -le 3 ]; do
          apk add --no-cache nodejs npm && exit 0
          \(nodeVerificationCommand) && exit 0
          sleep $((attempt * 2))
          attempt=$((attempt + 1))
        done
        exit 1
        """

    private func markRuntimeReady() {
        do {
            try Data().write(to: alpineSetupCompleteMarkerURL(), options: .atomic)
            initialized = true
            reportInitializationProgress(
                phase: "ready",
                detail: "Alpine is ready",
                fraction: 1.0
            )
            finishInitialization(error: nil)
        } catch {
            finishInitialization(error: error.localizedDescription)
        }
    }

    private func reportInitializationProgress(phase: String, detail: String, fraction: Double) {
        let listeners = initializationListeners
        onMain {
            listeners.forEach {
                $0.onProgress(phase: phase, detail: detail, fraction: fraction)
            }
        }
    }

    private func reportInitializationOutput(_ data: Data) {
        guard !data.isEmpty else { return }
        let text = String(decoding: data, as: UTF8.self)
        let listeners = initializationListeners
        onMain { listeners.forEach { $0.onOutput(text: text) } }
    }

    private func finishInitialization(error: String?) {
        let listeners = initializationListeners
        initializationListeners.removeAll()
        initializationInProgress = false
        onMain {
            listeners.forEach { listener in
                if let error {
                    listener.onError(message: error)
                } else {
                    listener.onReady()
                }
            }
        }
    }

    func startProcess(
        executable: String,
        arguments: [String],
        environment: [String: String],
        workingDirectory: String,
        redirectErrorStream: Bool,
        interactiveTerminal: Bool,
        remoteDebuggingPipe: Bool,
        listener: NativeRuntimeProcessListener
    ) -> Int64 {
        let stdout: AetherISHOutputBlock = { data in
            listener.onStdout(bytes: data.kotlinByteArray)
        }
        let stderr: AetherISHOutputBlock = { data in
            if redirectErrorStream {
                listener.onStdout(bytes: data.kotlinByteArray)
            } else {
                listener.onStderr(bytes: data.kotlinByteArray)
            }
        }
        let pid = runtime.startExecutable(
            executable,
            arguments: arguments,
            environment: environment,
            workingDirectory: workingDirectory,
            pseudoTerminal: interactiveTerminal,
            remoteDebuggingPipe: remoteDebuggingPipe,
            standardOutput: stdout,
            standardError: stderr,
            exit: { exitCode, signal in
                listener.onExit(exitCode: exitCode, signal: signal)
            }
        )
        return Int64(pid)
    }

    func writeStdin(processId: Int64, bytes: KotlinByteArray) -> Bool {
        runtime.writeStdin(bytes.data, processId: Int32(processId))
    }

    func closeStdin(processId: Int64) {
        runtime.closeStdin(forProcessId: Int32(processId))
    }

    func signal(processId: Int64, signal: Int32) {
        runtime.signalProcessId(Int32(processId), signal: signal)
    }

    func resizeTerminal(processId: Int64, columns: Int32, rows: Int32) {
        runtime.resizeTerminal(forProcessId: Int32(processId), columns: columns, rows: rows)
    }

    func createTerminalView(listener: NativeTerminalViewListener) -> Any {
        let view = AetherTerminalView(frame: .zero)
        view.onInput = { data in listener.onInput(bytes: data.kotlinByteArray) }
        view.onResize = { columns, rows in
            listener.onResize(columns: Int32(columns), rows: Int32(rows))
        }
        view.onTitleChanged = { title in listener.onTitleChanged(title: title) }
        return view
    }

    func updateTerminalView(view: Any, bytes: KotlinByteArray) {
        (view as? AetherTerminalView)?.feed(bytes.data)
    }

    func setTerminalDarkTheme(view: Any, darkTheme: Bool) {
        (view as? AetherTerminalView)?.setDarkTheme(darkTheme)
    }

    func focusTerminalView(view: Any) {
        (view as? AetherTerminalView)?.focus()
    }

    func sendTerminalKey(view: Any, key: String, controlDown: Bool, altDown: Bool) {
        (view as? AetherTerminalView)?.sendKey(key, control: controlDown, alt: altDown)
    }

    func destroyTerminalView(view: Any) {
        (view as? AetherTerminalView)?.cleanup()
    }

    func fileExists(path: String, listener: NativeBooleanResultListener) {
        operations.async { [self] in
            guard runtime.isInitialized else {
                complete { listener.onSuccess(value: false) }
                return
            }
            complete { listener.onSuccess(value: self.runtime.fileExists(path)) }
        }
    }

    func createDirectories(path: String, listener: NativeUnitResultListener) {
        operations.async { [self] in
            complete(listener: listener) { try guestCreateDirectories(path) }
        }
    }

    func readFile(path: String, listener: NativeBytesResultListener) {
        operations.async { [self] in
            do {
                let data = try runtime.readFile(path)
                complete { listener.onSuccess(value_: data.kotlinByteArray) }
            } catch {
                complete { listener.onError(message: error.localizedDescription) }
            }
        }
    }

    func readFile(path: String, maximumBytes: Int64, listener: NativeBytesResultListener) {
        operations.async { [self] in
            do {
                guard maximumBytes >= 0, maximumBytes <= Int64(Int.max) else {
                    throw NSError(
                        domain: "com.baimoqilin.aether.runtime-host",
                        code: 1,
                        userInfo: [NSLocalizedDescriptionKey: "Invalid file size limit."]
                    )
                }
                let data = try runtime.readFile(path, maximumBytes: UInt(maximumBytes))
                complete { listener.onSuccess(value_: data.kotlinByteArray) }
            } catch {
                complete { listener.onError(message: error.localizedDescription) }
            }
        }
    }

    func readFilePrefix(path: String, maximumBytes: Int64, listener: NativeBytesResultListener) {
        operations.async { [self] in
            do {
                guard maximumBytes >= 0, maximumBytes <= Int64(Int.max) else {
                    throw NSError(
                        domain: "com.baimoqilin.aether.runtime-host",
                        code: 1,
                        userInfo: [NSLocalizedDescriptionKey: "Invalid file size limit."]
                    )
                }
                let data = try runtime.readFilePrefix(path, maximumBytes: UInt(maximumBytes))
                complete { listener.onSuccess(value_: data.kotlinByteArray) }
            } catch {
                complete { listener.onError(message: error.localizedDescription) }
            }
        }
    }

    func writeFile(path: String, bytes: KotlinByteArray, executable: Bool, listener: NativeUnitResultListener) {
        operations.async { [self] in
            complete(listener: listener) {
                try runtime.writeFile(path, data: bytes.data, executable: executable)
            }
        }
    }

    func writeFileWithProgress(
        path: String,
        bytes: KotlinByteArray,
        executable: Bool,
        listener: NativeFileWriteListener
    ) {
        operations.async { [self] in
            do {
                try runtime.writeFile(
                    path,
                    data: bytes.data,
                    executable: executable,
                    progress: { bytesCopied in
                        complete {
                            listener.onProgress(bytesCopied: Int64(bytesCopied))
                        }
                    }
                )
                complete { listener.onSuccess() }
            } catch {
                complete { listener.onError(message: error.localizedDescription) }
            }
        }
    }

    func remove(path: String, recursive: Bool, listener: NativeUnitResultListener) {
        operations.async { [self] in
            complete(listener: listener) {
                try runtime.removePath(path, recursive: recursive)
            }
        }
    }

    func bindHostDirectory(hostPath: String, guestPath: String, readOnly: Bool, listener: NativeUnitResultListener) {
        operations.async { [self] in
            complete(listener: listener) { try guestBind(hostPath: hostPath, guestPath: guestPath, readOnly: readOnly) }
        }
    }

    func pickFile(imagesOnly: Bool, listener: NativePickedFileListener) {
        onMain { [self] in
            guard !hasActiveDocumentPicker else {
                listener.onError(message: "Another file picker is already open.")
                return
            }
            guard let presenter = topViewController() else {
                listener.onError(message: "Unable to present the file picker.")
                return
            }
            filePickerListener = listener
            let types: [UTType] = imagesOnly ? [.image] : [.item]
            let picker = UIDocumentPickerViewController(forOpeningContentTypes: types, asCopy: true)
            picker.delegate = self
            picker.allowsMultipleSelection = false
            presenter.present(picker, animated: true)
        }
    }

    func pickFiles(imagesOnly: Bool, listener: NativePickedFilesListener) {
        onMain { [self] in
            guard !hasActiveDocumentPicker else {
                listener.onError(message: "Another file picker is already open.")
                return
            }
            guard let presenter = topViewController() else {
                listener.onError(message: "Unable to present the file picker.")
                return
            }
            filesPickerListener = listener
            if imagesOnly {
                var configuration = PHPickerConfiguration(photoLibrary: .shared())
                configuration.filter = .images
                configuration.selectionLimit = 0
                configuration.preferredAssetRepresentationMode = .current
                let picker = PHPickerViewController(configuration: configuration)
                picker.delegate = self
                presenter.present(picker, animated: true)
            } else {
                let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.item], asCopy: true)
                picker.delegate = self
                picker.allowsMultipleSelection = true
                presenter.present(picker, animated: true)
            }
        }
    }

    func pickDirectory(listener: NativePickedDirectoryListener) {
        onMain { [self] in
            guard !hasActiveDocumentPicker else {
                listener.onError(message: "Another file picker is already open.")
                return
            }
            guard let presenter = topViewController() else {
                listener.onError(message: "Unable to present the folder picker.")
                return
            }
            directoryPickerListener = listener
            // Folder imports must keep the provider-backed URL in place. Asking the
            // document picker to copy a directory can crash when the provider commits
            // the selection; read it under its security-scoped access instead.
            let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.folder], asCopy: false)
            picker.delegate = self
            picker.allowsMultipleSelection = false
            presenter.present(picker, animated: true)
        }
    }

    func openUrl(url: String) -> Bool {
        guard let target = URL(string: url), UIApplication.shared.canOpenURL(target) else { return false }
        onMain { UIApplication.shared.open(target) }
        return true
    }

    func openAuthenticationUrl(url: String, listener: NativeAuthenticationSessionListener) -> Bool {
        guard let target = URL(string: url), ["http", "https"].contains(target.scheme?.lowercased()) else {
            return false
        }
        onMain { [weak self] in
            guard let self else { return }
            self.authenticationSession?.cancel()
            let session = ASWebAuthenticationSession(
                url: target,
                callbackURLScheme: "http"
            ) { [weak self] callbackURL, error in
                self?.authenticationSession = nil
                if let callbackURL {
                    listener.onCallback(url: callbackURL.absoluteString)
                } else if let sessionError = error as? ASWebAuthenticationSessionError,
                          sessionError.code == .canceledLogin {
                    listener.onCancelled()
                } else {
                    listener.onError(message: error?.localizedDescription ?? "Authentication session failed.")
                }
            }
            session.presentationContextProvider = self
            session.prefersEphemeralWebBrowserSession = false
            self.authenticationSession = session
            if !session.start() {
                self.authenticationSession = nil
            }
        }
        return true
    }

    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first { $0.isKeyWindow }
            ?? ASPresentationAnchor()
    }

    func terminateApplication() -> Bool {
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            exit(EXIT_SUCCESS)
        }
        return true
    }

    func doCopyText(text: String) -> Bool {
        UIPasteboard.general.string = text
        return true
    }

    func shareText(title: String, text: String) -> Bool {
        guard let presenter = topViewController() else { return false }
        let controller = UIActivityViewController(activityItems: [text], applicationActivities: nil)
        controller.title = title
        if let popover = controller.popoverPresentationController {
            popover.sourceView = presenter.view
            popover.sourceRect = CGRect(
                x: presenter.view.bounds.midX,
                y: presenter.view.bounds.maxY - 1,
                width: 1,
                height: 1
            )
        }
        presenter.present(controller, animated: true)
        return true
    }

    func shareFile(name: String, mimeType: String, bytes: KotlinByteArray) -> Bool {
        guard let presenter = topViewController(), let url = temporaryFile(name: name, bytes: bytes.data) else {
            return false
        }
        let controller = UIActivityViewController(activityItems: [url], applicationActivities: nil)
        if let popover = controller.popoverPresentationController {
            popover.sourceView = presenter.view
            popover.sourceRect = CGRect(x: presenter.view.bounds.midX, y: presenter.view.bounds.maxY - 1, width: 1, height: 1)
        }
        presenter.present(controller, animated: true)
        return true
    }

    func exportFile(name: String, mimeType: String, bytes: KotlinByteArray, listener: NativeFileExportListener) {
        onMain { [self] in
            guard !hasActiveDocumentPicker else {
                listener.onError(message: "Another file picker is already open.")
                return
            }
            guard let presenter = topViewController(), let url = temporaryFile(name: name, bytes: bytes.data) else {
                listener.onError(message: "Unable to prepare the file for export.")
                return
            }
            fileExportListener = listener
            fileExportURL = url
            let picker = UIDocumentPickerViewController(forExporting: [url], asCopy: true)
            picker.delegate = self
            presenter.present(picker, animated: true)
        }
    }

    func previewFile(name: String, mimeType: String, bytes: KotlinByteArray) -> Bool {
        guard let presenter = topViewController(), let url = temporaryFile(name: name, bytes: bytes.data) else {
            return false
        }
        previewURL = url
        let controller = QLPreviewController()
        controller.dataSource = self
        presenter.present(controller, animated: true)
        return true
    }

    func openAlpineFileManager() -> Bool {
        onMain {
            NotificationCenter.default.post(name: .openAlpineFileManager, object: nil)
        }
        return true
    }

    func listGuestDirectory(
        path: String,
        completion: @escaping (Result<[AlpineFileEntry], Error>) -> Void
    ) {
        operations.async { [self] in
            do {
                let values = try runtime.listDirectory(path)
                let entries = values.compactMap { value -> AlpineFileEntry? in
                    guard let name = value["name"] as? String else { return nil }
                    let childPath = path == "/" ? "/\(name)" : "\(path)/\(name)"
                    return AlpineFileEntry(
                        name: name,
                        path: childPath,
                        isDirectory: (value["directory"] as? NSNumber)?.boolValue ?? false,
                        isSymbolicLink: (value["symbolicLink"] as? NSNumber)?.boolValue ?? false,
                        size: (value["size"] as? NSNumber)?.int64Value ?? 0,
                        modifiedAt: Date(
                            timeIntervalSince1970: (value["modified"] as? NSNumber)?.doubleValue ?? 0
                        ),
                        mode: (value["mode"] as? NSNumber)?.uint32Value ?? 0
                    )
                }
                complete { completion(.success(entries)) }
            } catch {
                complete { completion(.failure(error)) }
            }
        }
    }

    func createGuestFile(path: String, completion: @escaping (Result<Void, Error>) -> Void) {
        operations.async { [self] in
            do {
                guard !runtime.fileExists(path) else {
                    throw RuntimeHostError.operationFailed("An item already exists at \(path).")
                }
                try runtime.writeFile(path, data: Data(), executable: false)
                complete { completion(.success(())) }
            } catch {
                complete { completion(.failure(error)) }
            }
        }
    }

    func createGuestDirectory(path: String, completion: @escaping (Result<Void, Error>) -> Void) {
        operations.async { [self] in
            do {
                try runtime.createDirectories(path)
                complete { completion(.success(())) }
            } catch {
                complete { completion(.failure(error)) }
            }
        }
    }

    func moveGuestPath(
        from source: String,
        to destination: String,
        completion: @escaping (Result<Void, Error>) -> Void
    ) {
        operations.async { [self] in
            do {
                guard source == destination || !runtime.fileExists(destination) else {
                    throw RuntimeHostError.operationFailed("An item already exists at \(destination).")
                }
                guard source != destination else {
                    complete { completion(.success(())) }
                    return
                }
                try runtime.movePath(source, toPath: destination)
                complete { completion(.success(())) }
            } catch {
                complete { completion(.failure(error)) }
            }
        }
    }

    func removeGuestPath(
        path: String,
        recursive: Bool,
        completion: @escaping (Result<Void, Error>) -> Void
    ) {
        operations.async { [self] in
            do {
                try runtime.removePath(path, recursive: recursive)
                complete { completion(.success(())) }
            } catch {
                complete { completion(.failure(error)) }
            }
        }
    }

    func exportGuestFile(
        path: String,
        openWithOtherApp: Bool = false,
        completion: @escaping (Result<Void, Error>) -> Void
    ) {
        operations.async { [self] in
            do {
                guard path.hasPrefix("/"), path != "/" else {
                    throw RuntimeHostError.operationFailed("The Alpine file path is invalid.")
                }
                let directory = FileManager.default.temporaryDirectory
                    .appendingPathComponent("AetherAlpineExports", isDirectory: true)
                    .appendingPathComponent(UUID().uuidString, isDirectory: true)
                try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
                let name = URL(fileURLWithPath: path).lastPathComponent.replacingOccurrences(of: "/", with: "-")
                let source = directory.appendingPathComponent(name, isDirectory: false)
                do {
                    try runtime.exportFile(path, to: source)
                } catch {
                    try? FileManager.default.removeItem(at: directory)
                    throw error
                }

                complete { [self] in
                    guard !hasActiveDocumentPicker else {
                        try? FileManager.default.removeItem(at: directory)
                        completion(.failure(RuntimeHostError.operationFailed("Another file picker is already open.")))
                        return
                    }
                    guard let presenter = topViewController() else {
                        try? FileManager.default.removeItem(at: directory)
                        completion(.failure(RuntimeHostError.operationFailed("Unable to open the file exporter.")))
                        return
                    }
                    if openWithOtherApp {
                        let controller = UIActivityViewController(activityItems: [source], applicationActivities: nil)
                        controller.completionWithItemsHandler = { _, _, _, error in
                            try? FileManager.default.removeItem(at: directory)
                            if let error { completion(.failure(error)) }
                            else { completion(.success(())) }
                        }
                        if let popover = controller.popoverPresentationController {
                            popover.sourceView = presenter.view
                            popover.sourceRect = CGRect(x: presenter.view.bounds.midX, y: presenter.view.bounds.maxY - 1, width: 1, height: 1)
                        }
                        presenter.present(controller, animated: true)
                        return
                    }
                    guestFileExportCompletion = completion
                    fileExportURL = source
                    let picker = UIDocumentPickerViewController(forExporting: [source], asCopy: true)
                    picker.delegate = self
                    presenter.present(picker, animated: true)
                }
            } catch {
                complete { completion(.failure(error)) }
            }
        }
    }

    func readGuestFile(
        path: String,
        maximumBytes: Int = 8 * 1024 * 1024,
        completion: @escaping (Result<Data, Error>) -> Void
    ) {
        operations.async { [self] in
            do {
                let data = try runtime.readFile(path, maximumBytes: UInt(maximumBytes))
                complete { completion(.success(data)) }
            } catch {
                complete { completion(.failure(error)) }
            }
        }
    }

    func writeGuestFile(
        path: String,
        data: Data,
        completion: @escaping (Result<Void, Error>) -> Void
    ) {
        operations.async { [self] in
            do {
                try runtime.writeFile(path, data: data, executable: false)
                complete { completion(.success(())) }
            } catch {
                complete { completion(.failure(error)) }
            }
        }
    }

    func importGuestItems(
        urls: [URL],
        destinationDirectory: String,
        completion: @escaping (Result<Void, Error>) -> Void
    ) {
        operations.async { [self] in
            do {
                let imports = try urls.map { source -> (source: URL, name: String, destination: String) in
                    let name = source.lastPathComponent.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !name.isEmpty, name != ".", name != "..", !name.contains("/") else {
                        throw RuntimeHostError.operationFailed("The selected item has an invalid name.")
                    }
                    let destination = destinationDirectory == "/"
                        ? "/\(name)"
                        : "\(destinationDirectory)/\(name)"
                    return (source, name, destination)
                }
                guard Set(imports.map(\.name)).count == imports.count else {
                    throw RuntimeHostError.operationFailed("The selection contains items with the same name.")
                }
                for item in imports where runtime.fileExists(item.destination) {
                    throw RuntimeHostError.operationFailed("An item named \(item.name) already exists.")
                }

                for item in imports {
                    let scoped = item.source.startAccessingSecurityScopedResource()
                    do {
                        try importGuestItem(from: item.source, to: item.destination)
                        if scoped { item.source.stopAccessingSecurityScopedResource() }
                    } catch {
                        if scoped { item.source.stopAccessingSecurityScopedResource() }
                        if runtime.fileExists(item.destination) {
                            try? runtime.removePath(item.destination, recursive: true)
                        }
                        throw error
                    }
                }
                complete { completion(.success(())) }
            } catch {
                complete { completion(.failure(error)) }
            }
        }
    }

    private func importGuestItem(from source: URL, to destination: String) throws {
        let keys: Set<URLResourceKey> = [.isDirectoryKey, .isRegularFileKey, .isSymbolicLinkKey]
        let values = try source.resourceValues(forKeys: keys)
        guard values.isSymbolicLink != true else {
            throw RuntimeHostError.operationFailed("Symbolic links cannot be imported.")
        }
        if values.isDirectory == true {
            try runtime.createDirectories(destination)
            let children = try FileManager.default.contentsOfDirectory(
                at: source,
                includingPropertiesForKeys: Array(keys),
                options: []
            )
            for child in children {
                let name = child.lastPathComponent.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !name.isEmpty, name != ".", name != "..", !name.contains("/") else {
                    throw RuntimeHostError.operationFailed("The selected folder contains an item with an invalid name.")
                }
                try importGuestItem(from: child, to: "\(destination)/\(name)")
            }
            return
        }
        guard values.isRegularFile == true else {
            throw RuntimeHostError.operationFailed("The selected item is not a regular file or folder.")
        }
        try runtime.writeFile(
            destination,
            data: Data(contentsOf: source, options: .mappedIfSafe),
            executable: false
        )
    }

    func numberOfPreviewItems(in controller: QLPreviewController) -> Int { previewURL == nil ? 0 : 1 }

    func previewController(_ controller: QLPreviewController, previewItemAt index: Int) -> QLPreviewItem {
        previewURL! as NSURL
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        if let completion = takeGuestFileExportCompletion() {
            completion(.success(()))
            return
        }
        if let listener = takeFileExportListener() {
            listener.onCompleted()
            return
        }
        if let listener = takeDirectoryPickerListener() {
            guard let url = urls.first else {
                listener.onCancelled()
                return
            }
            do {
                try readPickedDirectory(url, listener: listener)
            } catch {
                listener.onError(message: error.localizedDescription)
            }
            return
        }
        if let listener = takeFilePickerListener() {
            guard let url = urls.first else {
                listener.onCancelled()
                return
            }
            do {
                let file = try readPickedFile(url)
                listener.onSelected(name: file.name, mimeType: file.mimeType, bytes: file.data.kotlinByteArray)
            } catch {
                listener.onError(message: error.localizedDescription)
            }
            return
        }
        guard let listener = takeFilesPickerListener() else { return }
        do {
            for url in urls {
                let file = try readPickedFile(url)
                listener.onSelected(name: file.name, mimeType: file.mimeType, bytes: file.data.kotlinByteArray)
            }
            listener.onCompleted()
        } catch {
            listener.onError(message: error.localizedDescription)
        }
    }

    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        if let completion = takeGuestFileExportCompletion() { completion(.success(())) }
        else if let listener = takeFileExportListener() { listener.onCancelled() }
        else if let listener = takeFilePickerListener() { listener.onCancelled() }
        else if let listener = takeFilesPickerListener() { listener.onCancelled() }
        else { takeDirectoryPickerListener()?.onCancelled() }
    }

    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        picker.dismiss(animated: true)
        guard let listener = takeFilesPickerListener() else { return }
        guard !results.isEmpty else {
            listener.onCancelled()
            return
        }

        let group = DispatchGroup()
        let lock = NSLock()
        var selected = Array<(name: String, mimeType: String, data: Data)?>(repeating: nil, count: results.count)
        var firstError: Error?
        for (index, result) in results.enumerated() {
            let provider = result.itemProvider
            guard let typeIdentifier = provider.registeredTypeIdentifiers.first(where: {
                UTType($0)?.conforms(to: .image) == true
            }) else {
                firstError = RuntimeHostError.operationFailed("The selected item is not a supported image.")
                continue
            }
            group.enter()
            provider.loadDataRepresentation(forTypeIdentifier: typeIdentifier) { [weak self] data, error in
                defer { group.leave() }
                lock.lock()
                defer { lock.unlock() }
                if let error {
                    if firstError == nil { firstError = error }
                    return
                }
                guard let self, let data else {
                    if firstError == nil {
                        firstError = RuntimeHostError.operationFailed("Unable to read the selected image.")
                    }
                    return
                }
                let type = UTType(typeIdentifier)
                var name = provider.suggestedName?.trimmingCharacters(in: .whitespacesAndNewlines) ?? ""
                if name.isEmpty { name = "image" }
                if URL(fileURLWithPath: name).pathExtension.isEmpty,
                   let fileExtension = type?.preferredFilenameExtension {
                    name += ".\(fileExtension)"
                }
                selected[index] = (
                    name: name,
                    mimeType: type?.preferredMIMEType ?? "image/*",
                    data: data
                )
            }
        }
        group.notify(queue: .main) {
            if let firstError {
                listener.onError(message: firstError.localizedDescription)
                return
            }
            for file in selected.compactMap({ $0 }) {
                listener.onSelected(
                    name: file.name,
                    mimeType: file.mimeType,
                    bytes: file.data.kotlinByteArray
                )
            }
            listener.onCompleted()
        }
    }

    private func takeFilePickerListener() -> NativePickedFileListener? {
        let listener = filePickerListener
        filePickerListener = nil
        return listener
    }

    private func takeFilesPickerListener() -> NativePickedFilesListener? {
        let listener = filesPickerListener
        filesPickerListener = nil
        return listener
    }

    private func takeDirectoryPickerListener() -> NativePickedDirectoryListener? {
        let listener = directoryPickerListener
        directoryPickerListener = nil
        return listener
    }

    private var hasActiveDocumentPicker: Bool {
        filePickerListener != nil || filesPickerListener != nil || directoryPickerListener != nil ||
            fileExportListener != nil || guestFileExportCompletion != nil
    }

    private func takeGuestFileExportCompletion() -> ((Result<Void, Error>) -> Void)? {
        let completion = guestFileExportCompletion
        guestFileExportCompletion = nil
        if completion != nil, let url = fileExportURL {
            try? FileManager.default.removeItem(at: url.deletingLastPathComponent())
            fileExportURL = nil
        }
        return completion
    }

    private func takeFileExportListener() -> NativeFileExportListener? {
        let listener = fileExportListener
        fileExportListener = nil
        if let url = fileExportURL {
            try? FileManager.default.removeItem(at: url)
        }
        fileExportURL = nil
        return listener
    }

    private func readPickedFile(_ url: URL) throws -> (name: String, mimeType: String, data: Data) {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        let data = try Data(contentsOf: url, options: .mappedIfSafe)
        return (
            url.lastPathComponent,
            UTType(filenameExtension: url.pathExtension)?.preferredMIMEType ?? "application/octet-stream",
            data
        )
    }

    private func readPickedDirectory(_ root: URL, listener: NativePickedDirectoryListener) throws {
        let scoped = root.startAccessingSecurityScopedResource()
        defer { if scoped { root.stopAccessingSecurityScopedResource() } }

        let keys: [URLResourceKey] = [.isRegularFileKey, .fileSizeKey]
        var enumerationError: Error?
        guard let enumerator = FileManager.default.enumerator(
            at: root,
            includingPropertiesForKeys: keys,
            options: [.skipsPackageDescendants],
            errorHandler: { _, error in
                enumerationError = error
                return false
            }
        ) else {
            throw NSError(
                domain: "com.baimoqilin.aether.file-picker",
                code: 2,
                userInfo: [NSLocalizedDescriptionKey: "Unable to read the selected folder."]
            )
        }

        var files: [(url: URL, relativePath: String, size: Int)] = []
        var totalBytes = 0
        for case let fileURL as URL in enumerator {
            let values = try fileURL.resourceValues(forKeys: Set(keys))
            guard values.isRegularFile == true else { continue }
            let relativePath = String(fileURL.path.dropFirst(root.path.count))
                .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            guard !relativePath.isEmpty,
                  !relativePath.split(separator: "/").contains("..") else {
                continue
            }
            let size = values.fileSize ?? 0
            guard size <= maximumPickedDirectoryEntryBytes else {
                throw NSError(
                    domain: "com.baimoqilin.aether.file-picker",
                    code: 3,
                    userInfo: [NSLocalizedDescriptionKey: "A file in the selected folder is too large: \(relativePath)"]
                )
            }
            files.append((fileURL, relativePath, size))
            guard files.count <= maximumPickedDirectoryEntries else {
                throw NSError(
                    domain: "com.baimoqilin.aether.file-picker",
                    code: 4,
                    userInfo: [NSLocalizedDescriptionKey: "The selected folder contains too many files."]
                )
            }
            totalBytes += size
            guard totalBytes <= maximumPickedDirectoryBytes else {
                throw NSError(
                    domain: "com.baimoqilin.aether.file-picker",
                    code: 5,
                    userInfo: [NSLocalizedDescriptionKey: "The selected folder is too large."]
                )
            }
        }
        if let enumerationError { throw enumerationError }

        for file in files.sorted(by: { $0.relativePath < $1.relativePath }) {
            let data = try Data(contentsOf: file.url, options: .mappedIfSafe)
            guard data.count <= maximumPickedDirectoryEntryBytes else {
                throw NSError(
                    domain: "com.baimoqilin.aether.file-picker",
                    code: 3,
                    userInfo: [NSLocalizedDescriptionKey: "A file in the selected folder is too large: \(file.relativePath)"]
                )
            }
            let mimeType = UTType(filenameExtension: file.url.pathExtension)?.preferredMIMEType
                ?? "application/octet-stream"
            listener.onSelected(
                relativePath: file.relativePath,
                mimeType: mimeType,
                bytes: data.kotlinByteArray
            )
        }
        listener.onCompleted(name: root.lastPathComponent)
    }

    private func temporaryFile(name: String, bytes: Data) -> URL? {
        let safeName = name.replacingOccurrences(of: "/", with: "-")
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent("AetherSharedFiles", isDirectory: true)
        do {
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            let url = directory.appendingPathComponent(safeName)
            try bytes.write(to: url, options: .atomic)
            return url
        } catch {
            return nil
        }
    }

    private func topViewController() -> UIViewController? {
        let root = UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow)?
            .rootViewController
        var current = root
        while let presented = current?.presentedViewController { current = presented }
        return current
    }

    private func workspaceURL() throws -> URL {
        let documents = try FileManager.default.url(
            for: .documentDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        try FileManager.default.createDirectory(at: documents, withIntermediateDirectories: true)

        // Keep the system Files location and Alpine's /workspace on the same host
        // directory. Migrate the pre-file-sharing workspace once without replacing
        // anything the user already created through Files.
        let support = try FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        let legacy = support.appendingPathComponent("Workspace", isDirectory: true)
        if FileManager.default.fileExists(atPath: legacy.path) {
            for source in try FileManager.default.contentsOfDirectory(
                at: legacy,
                includingPropertiesForKeys: nil
            ) {
                let destination = documents.appendingPathComponent(source.lastPathComponent)
                if !FileManager.default.fileExists(atPath: destination.path) {
                    try FileManager.default.moveItem(at: source, to: destination)
                }
            }
            try? FileManager.default.removeItem(at: legacy)
        }
        return documents
    }

    private func alpineRuntimeRootURL() throws -> URL {
        if let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: "group.com.baimoqilin.aether") {
            return container.appendingPathComponent("AetherAlpine", isDirectory: true)
        }
        let applicationSupport = try FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        return applicationSupport.appendingPathComponent("AetherAlpine", isDirectory: true)
    }

    private func alpineResetMarkerURL() throws -> URL {
        let applicationSupport = try FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        return applicationSupport.appendingPathComponent(".AetherAlpineResetPending", isDirectory: false)
    }

    private func alpineSetupCompleteMarkerURL() throws -> URL {
        try alpineRuntimeRootURL()
            .appendingPathComponent(".aether-setup-complete", isDirectory: false)
    }

    private func purgePendingAlpineReset() throws {
        let marker = try alpineResetMarkerURL()
        guard FileManager.default.fileExists(atPath: marker.path) else { return }
        guard !runtime.isInitialized else {
            throw NSError(
                domain: "com.baimoqilin.aether.runtime-host",
                code: 2,
                userInfo: [NSLocalizedDescriptionKey: "Restart Aether to finish resetting Alpine."]
            )
        }
        let root = try alpineRuntimeRootURL()
        if FileManager.default.fileExists(atPath: root.path) {
            try FileManager.default.removeItem(at: root)
        }
        try FileManager.default.removeItem(at: marker)
        initialized = false
    }

    private func chromeRuntimeURL() throws -> URL {
        let support = try FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        let runtime = support.appendingPathComponent("ChromiumRuntime", isDirectory: true)
        try FileManager.default.createDirectory(at: runtime, withIntermediateDirectories: true)
        return runtime
    }

    private func chromeDependenciesURL() throws -> URL {
        let support = try FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        let dependencies = support.appendingPathComponent("ChromiumDependencies", isDirectory: true)
        try FileManager.default.createDirectory(at: dependencies, withIntermediateDirectories: true)
        return dependencies
    }

    private func installBridgeAsset() throws {
        try guestCreateDirectories("/root/.aether/pi-bridge")
        try installBridgeAsset(
            resource: "bridge",
            guestName: "bridge.mjs",
            markerName: ".bridge.sha256"
        )
        try installBridgeAsset(
            resource: "extension-bridge",
            guestName: "extension-bridge.mjs",
            markerName: ".extension-bridge.sha256"
        )
        try installBridgeAsset(
            resource: "image-resize-worker",
            fileExtension: "js",
            guestName: "image-resize-worker.js",
            markerName: ".image-resize-worker.sha256"
        )
        try installBridgeAsset(
            resource: "photon_rs_bg",
            fileExtension: "wasm",
            guestName: "photon_rs_bg.wasm",
            markerName: ".photon_rs_bg.sha256"
        )
    }

    private func installBridgeAsset(
        resource: String,
        fileExtension: String = "mjs",
        guestName: String,
        markerName: String
    ) throws {
        guard let source = Bundle.main.url(forResource: resource, withExtension: fileExtension) else {
            throw RuntimeHostError.operationFailed("Bundled \(resource) is missing.")
        }
        let bytes = try Data(contentsOf: source)
        let fingerprint = SHA256.hash(data: bytes)
            .map { String(format: "%02x", $0) }
            .joined()
        let bridgePath = "/root/.aether/pi-bridge/\(guestName)"
        let markerPath = "/root/.aether/pi-bridge/\(markerName)"
        if runtime.fileExists(bridgePath),
           let marker = try? runtime.readFile(markerPath),
           String(data: marker, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines) == fingerprint {
            return
        }
        try runtime.writeFile(
            bridgePath,
            data: bytes,
            executable: false
        )
        try runtime.writeFile(
            markerPath,
            data: Data("\(fingerprint)\n".utf8),
            executable: false
        )
    }

    private func installPreinstalledExtensions() throws {
        let candidates = [
            Bundle.main.url(forResource: "extensions", withExtension: nil),
            Bundle.main.resourceURL?.appendingPathComponent("extensions", isDirectory: true),
            Bundle.main.resourceURL?.appendingPathComponent("Runtime/extensions", isDirectory: true),
        ].compactMap { $0 }
        guard let sourceDir = candidates.first(where: { FileManager.default.fileExists(atPath: $0.path) }) else {
            return
        }
        let guestBase = "/root/.aether/extensions"
        try guestCreateDirectories(guestBase)
        let fileManager = FileManager.default
        guard let enumerator = fileManager.enumerator(at: sourceDir, includingPropertiesForKeys: [.isDirectoryKey]) else {
            return
        }
        while let fileURL = enumerator.nextObject() as? URL {
            let resourceValues = try fileURL.resourceValues(forKeys: [.isDirectoryKey])
            let relativePath = fileURL.path.replacingOccurrences(of: sourceDir.path, with: "").trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            guard !relativePath.isEmpty else { continue }
            let topLevelName = relativePath.split(separator: "/", maxSplits: 1).first.map(String.init) ?? ""
            let removalMarker = "/root/.aether/.removed-preinstalled-extensions/\(topLevelName)"
            if runtime.fileExists(removalMarker) {
                if resourceValues.isDirectory == true { enumerator.skipDescendants() }
                continue
            }
            let targetGuestPath = "\(guestBase)/\(relativePath)"
            if resourceValues.isDirectory == true {
                // A user import may have the same package directory. Preserve the
                // complete existing entry instead of merging bundled files into it.
                if runtime.fileExists(targetGuestPath) {
                    enumerator.skipDescendants()
                    continue
                }
                try guestCreateDirectories(targetGuestPath)
            } else {
                if runtime.fileExists(targetGuestPath) { continue }
                let parentGuestDir = (targetGuestPath as NSString).deletingLastPathComponent
                try guestCreateDirectories(parentGuestDir)
                let data = try Data(contentsOf: fileURL)
                try runtime.writeFile(targetGuestPath, data: data, executable: false)
            }
        }
    }

    private func installNodeCompatibilityAssets() throws {
        for name in ["wasm-polyfill", "fetch-polyfill"] {
            guard let source = Bundle.main.url(forResource: name, withExtension: "js") else {
                throw RuntimeHostError.operationFailed("Bundled Node compatibility asset \(name).js is missing.")
            }
            try runtime.writeFile(
                "/lib/\(name).js",
                data: Data(contentsOf: source),
                executable: false
            )
        }
    }

    private func configureApkRepositories(environment: AlpineNetworkEnvironment) throws {
        let repositoriesPath = "/etc/apk/repositories"
        guard runtime.fileExists(repositoriesPath) else { return }
        guard let original = try? runtime.readFile(repositoriesPath) else { return }
        guard let originalContents = String(data: original, encoding: .utf8) else { return }
        let contents = alpineRepositories(originalContents, environment: environment)
        guard contents != String(data: original, encoding: .utf8) else { return }
        try runtime.writeFile(
            repositoriesPath,
            data: Data(contents.utf8),
            executable: false
        )
    }

    private func guestCreateDirectories(_ path: String) throws {
        try runtime.createDirectories(path)
    }

    private func guestBind(hostPath: String, guestPath: String, readOnly: Bool = false) throws {
        try runtime.bindHostPath(hostPath, guestPath: guestPath, readOnly: readOnly)
    }

    private func complete(listener: NativeUnitResultListener, operation: () throws -> Void) {
        do {
            try operation()
            complete { listener.onSuccess() }
        } catch {
            complete { listener.onError(message: error.localizedDescription) }
        }
    }

    private func complete(_ callback: @escaping () -> Void) {
        onMain(callback)
    }

    private func onMain(_ callback: @escaping () -> Void) {
        if Thread.isMainThread {
            callback()
        } else {
            DispatchQueue.main.async(execute: callback)
        }
    }
}

private final class RuntimeReadinessInitializationListener: NSObject, NativeRuntimeInitializationListener {
    private let readyHandler: () -> Void
    private let errorHandler: (String) -> Void

    init(onReady: @escaping () -> Void, onError: @escaping (String) -> Void) {
        self.readyHandler = onReady
        self.errorHandler = onError
    }

    func onProgress(phase: String, detail: String, fraction: Double) {}
    func onOutput(text: String) {}
    func onReady() { readyHandler() }
    func onError(message: String) { errorHandler(message) }
}

private final class AetherBackgroundExecutionCoordinator {
    static let shared = AetherBackgroundExecutionCoordinator()

    private struct Lease {
        let name: String
        let onExpired: () -> Void
        var detail: String
    }

    private var leases: [String: Lease] = [:]
    private var briefTaskIdentifier: UIBackgroundTaskIdentifier = .invalid
    private var continuedTask: AnyObject?
    private var continuedTaskIdentifier: String?
    private var continuedTaskSubmitted = false
    private var pendingCompletionSuccess: Bool?
    private var progressTimer: DispatchSourceTimer?
    private var progressActivityCount: Int64 = 0
    private var lastProgressUpdate = Date.distantPast
    private var lastProgressAdvance = Date.distantPast

    private var taskIdentifierPrefix: String {
        "\(Bundle.main.bundleIdentifier ?? "com.baimoqilin.aether").agent"
    }

    func register() {
        onMainSync {
            guard #available(iOS 26.0, *) else { return }
            if UIApplication.shared.backgroundRefreshStatus != .available {
                NSLog("Aether background refresh is unavailable (status: %ld)",
                      UIApplication.shared.backgroundRefreshStatus.rawValue)
            }
        }
    }

    func begin(name: String, onExpired: @escaping () -> Void) -> String {
        onMainSync {
            let identifier = UUID().uuidString
            leases[identifier] = Lease(name: name, onExpired: onExpired, detail: "Starting")
            pendingCompletionSuccess = nil
            ensureBriefBackgroundTask(name: name)
            if #available(iOS 26.0, *) {
                ensureContinuedProcessingTask(name: name)
            }
            return identifier
        }
    }

    func update(identifier: String, detail: String) {
        onMain {
            guard var lease = self.leases[identifier] else { return }
            lease.detail = detail
            self.leases[identifier] = lease
            guard #available(iOS 26.0, *),
                  let task = self.continuedTask as? BGContinuedProcessingTask else { return }
            let now = Date()
            self.advanceProgress(task, now: now)
            guard now.timeIntervalSince(self.lastProgressUpdate) >= 1 else { return }
            self.lastProgressUpdate = now
            task.updateTitle(lease.name, subtitle: detail)
        }
    }

    func end(identifier: String, success: Bool) {
        onMain {
            guard self.leases.removeValue(forKey: identifier) != nil else { return }
            if self.leases.isEmpty {
                self.finishAll(success: success)
            }
        }
    }

    private func ensureBriefBackgroundTask(name: String) {
        guard briefTaskIdentifier == .invalid else { return }
        briefTaskIdentifier = UIApplication.shared.beginBackgroundTask(withName: name) { [weak self] in
            self?.onMain { self?.briefBackgroundTaskExpired() }
        }
    }

    @available(iOS 26.0, *)
    private func ensureContinuedProcessingTask(name: String) {
        let scheduler = BGTaskScheduler.shared
        guard continuedTask == nil, !continuedTaskSubmitted else { return }
        let identifier = "\(taskIdentifierPrefix).\(UUID().uuidString.lowercased())"
        let registered = scheduler.register(
            forTaskWithIdentifier: identifier,
            using: nil
        ) { [weak self] task in
            guard let self, let task = task as? BGContinuedProcessingTask else {
                task.setTaskCompleted(success: false)
                return
            }
            self.onMain { self.attach(task) }
        }
        guard registered else {
            NSLog("Aether continued-processing task handler was not registered for %@", identifier)
            return
        }
        continuedTaskIdentifier = identifier
        NSLog("Aether continued-processing task handler registered for %@", identifier)
        let request = BGContinuedProcessingTaskRequest(
            identifier: identifier,
            title: name,
            subtitle: "Running in background"
        )
        // Agent turns are user initiated. Queueing lets iOS start the continued
        // task as soon as conditions allow instead of rejecting it under load.
        request.strategy = .queue
        do {
            try scheduler.submit(request)
            continuedTaskSubmitted = true
            NSLog("Aether continued-processing task submitted for %@", identifier)
        } catch {
            continuedTaskSubmitted = false
            continuedTaskIdentifier = nil
            let nsError = error as NSError
            NSLog("Aether continued-processing task submission failed (%@/%ld): %@",
                  nsError.domain, nsError.code, nsError.localizedDescription)
        }
    }

    @available(iOS 26.0, *)
    private func attach(_ task: BGContinuedProcessingTask) {
        NSLog("Aether continued-processing task started for %@", task.identifier)
        guard task.identifier == continuedTaskIdentifier else {
            task.setTaskCompleted(success: false)
            return
        }
        continuedTaskSubmitted = false
        if let success = pendingCompletionSuccess {
            pendingCompletionSuccess = nil
            task.progress.totalUnitCount = 1
            task.progress.completedUnitCount = success ? 1 : 0
            task.setTaskCompleted(success: success)
            endBriefBackgroundTask()
            return
        }
        guard !leases.isEmpty else {
            task.setTaskCompleted(success: true)
            endBriefBackgroundTask()
            return
        }
        continuedTask = task
        progressActivityCount = 1
        lastProgressUpdate = .distantPast
        lastProgressAdvance = Date()
        task.progress.totalUnitCount = 10_000
        task.progress.completedUnitCount = progressActivityCount
        task.expirationHandler = { [weak self] in
            self?.onMain { self?.expireAll() }
        }
        startProgressHeartbeat()
        endBriefBackgroundTask()
    }

    @available(iOS 26.0, *)
    private func startProgressHeartbeat() {
        stopProgressHeartbeat()
        let timer = DispatchSource.makeTimerSource(queue: .main)
        timer.schedule(deadline: .now() + 15, repeating: 15, leeway: .seconds(1))
        timer.setEventHandler { [weak self] in
            guard let self,
                  !self.leases.isEmpty,
                  let task = self.continuedTask as? BGContinuedProcessingTask else { return }
            self.advanceProgress(task, now: Date())
        }
        progressTimer = timer
        timer.resume()
    }

    @available(iOS 26.0, *)
    private func advanceProgress(_ task: BGContinuedProcessingTask, now: Date) {
        guard now.timeIntervalSince(lastProgressAdvance) >= 14 else { return }
        lastProgressAdvance = now
        // Agent turns have no knowable total work. Move an activity proxy toward, but never
        // reach, completion so the scheduler can distinguish a long tool call from a stall.
        let remaining = 9_999 - progressActivityCount
        let increment = max(remaining / 120, 1)
        progressActivityCount = min(progressActivityCount + increment, 9_999)
        task.progress.completedUnitCount = progressActivityCount
    }

    private func stopProgressHeartbeat() {
        progressTimer?.setEventHandler {}
        progressTimer?.cancel()
        progressTimer = nil
    }

    private func briefBackgroundTaskExpired() {
        if #available(iOS 26.0, *), continuedTaskSubmitted {
            // The continued-processing request owns the work now. Ending this bridge
            // may suspend the app briefly, but the scheduler will resume it to attach.
            endBriefBackgroundTask()
            return
        }
        expireAll()
    }

    private func expireAll() {
        let callbacks = leases.values.map(\.onExpired)
        leases.removeAll()
        callbacks.forEach { $0() }
        finishAll(success: false)
    }

    private func finishAll(success: Bool) {
        if #available(iOS 26.0, *), let task = continuedTask as? BGContinuedProcessingTask {
            task.expirationHandler = nil
            if success {
                task.progress.completedUnitCount = task.progress.totalUnitCount
            }
            task.setTaskCompleted(success: success)
        } else if #available(iOS 26.0, *), continuedTaskSubmitted {
            // Cancelling a successfully submitted request is presented by the system as
            // Task Failed. Retain its result and complete it when the launch handler runs.
            pendingCompletionSuccess = success
        }
        continuedTask = nil
        if !continuedTaskSubmitted {
            pendingCompletionSuccess = nil
            continuedTaskIdentifier = nil
        }
        stopProgressHeartbeat()
        progressActivityCount = 0
        lastProgressUpdate = .distantPast
        lastProgressAdvance = .distantPast
        endBriefBackgroundTask()
    }

    private func endBriefBackgroundTask() {
        guard briefTaskIdentifier != .invalid else { return }
        let identifier = briefTaskIdentifier
        briefTaskIdentifier = .invalid
        UIApplication.shared.endBackgroundTask(identifier)
    }

    private func onMain(_ operation: @escaping () -> Void) {
        if Thread.isMainThread {
            operation()
        } else {
            DispatchQueue.main.async(execute: operation)
        }
    }

    private func onMainSync<T>(_ operation: () -> T) -> T {
        if Thread.isMainThread {
            return operation()
        }
        return DispatchQueue.main.sync(execute: operation)
    }
}

private enum RuntimeHostError: LocalizedError {
    case operationFailed(String)

    var errorDescription: String? {
        switch self {
        case .operationFailed(let message): message
        }
    }
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
            guard let baseAddress = buffer.baseAddress?.assumingMemoryBound(to: UInt8.self) else { return }
            for index in 0..<Int(size) {
                baseAddress[index] = UInt8(bitPattern: get(index: Int32(index)))
            }
        }
        return result
    }
}
