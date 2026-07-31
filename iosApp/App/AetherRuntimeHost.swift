import Foundation
import UIKit
import PhotosUI
import UniformTypeIdentifiers
import QuickLook
import Darwin
import BackgroundTasks
import AetherShared

final class AetherRuntimeHost: NSObject, NativeRuntimeHost, UIDocumentPickerDelegate, PHPickerViewControllerDelegate, QLPreviewControllerDataSource {
    static let shared = AetherRuntimeHost()

    private let runtime = AetherISHRuntime.shared()
    private let operations = DispatchQueue(label: "com.baimoqilin.aether.runtime-host")
    private var initialized = false
    private var filePickerListener: NativePickedFileListener?
    private var filesPickerListener: NativePickedFilesListener?
    private var directoryPickerListener: NativePickedDirectoryListener?
    private var fileExportListener: NativeFileExportListener?
    private var fileExportURL: URL?
    private var previewURL: URL?
    private let backgroundExecution = AetherBackgroundExecutionCoordinator.shared

    private let maximumPickedDirectoryEntries = 4_096
    private let maximumPickedDirectoryEntryBytes = 16 * 1024 * 1024
    private let maximumPickedDirectoryBytes = 128 * 1024 * 1024

    func isRuntimeReady(listener: NativeBooleanResultListener) {
        operations.async { [self] in
            do {
                try purgePendingAlpineReset()
                let applicationSupport = try FileManager.default.url(
                    for: .applicationSupportDirectory,
                    in: .userDomainMask,
                    appropriateFor: nil,
                    create: true
                )
                let root = applicationSupport.appendingPathComponent("AetherAlpine", isDirectory: true)
                let hasData = FileManager.default.fileExists(
                    atPath: root.appendingPathComponent("data", isDirectory: true).path
                )
                let hasDatabase = FileManager.default.fileExists(
                    atPath: root.appendingPathComponent("meta.db", isDirectory: false).path
                )
                onMain { listener.onSuccess(value: hasData && hasDatabase) }
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
            do {
                try purgePendingAlpineReset()
            } catch {
                onMain { listener.onError(message: error.localizedDescription) }
                return
            }
            if initialized {
                onMain { listener.onReady() }
                return
            }
            onMain { listener.onProgress(phase: "rootfs", detail: "Preparing Alpine", fraction: 0.02) }
            runtime.initialize(
                progress: { phase, detail, fraction in
                    self.onMain {
                        listener.onProgress(phase: phase, detail: detail, fraction: fraction)
                    }
                },
                completion: { error in
                    if let error {
                        self.onMain { listener.onError(message: error.localizedDescription) }
                        return
                    }
                    self.finishInitialization(listener: listener)
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

    private func finishInitialization(listener: NativeRuntimeInitializationListener) {
        operations.async { [self] in
            do {
                let workspace = try workspaceURL()
                let chromeRuntime = try chromeRuntimeURL()
                let chromeDependencies = try chromeDependenciesURL()
                try guestCreateDirectories("/workspace")
                try guestBind(hostPath: workspace.path, guestPath: "/workspace")
                try guestCreateDirectories("/usr/lib/chromium")
                try guestBind(hostPath: chromeRuntime.path, guestPath: "/usr/lib/chromium")
                try guestCreateDirectories("/opt/aether/chromium-deps")
                try guestBind(hostPath: chromeDependencies.path, guestPath: "/opt/aether/chromium-deps")
                try installBridgeAsset()
                try installNodeCompatibilityAssets()
            } catch {
                onMain { listener.onError(message: error.localizedDescription) }
                return
            }

            checkNode(listener: listener)
        }
    }

    private func checkNode(listener: NativeRuntimeInitializationListener) {
        onMain {
            listener.onProgress(phase: "checking_node", detail: "Checking Node 22", fraction: 0.82)
        }
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
                if code == 0 {
                    self.markRuntimeReady(listener: listener)
                } else {
                    self.installNode(listener: listener)
                }
            }
        )
        if pid < 0 {
            onMain { listener.onError(message: "Unable to check Node 22 in Alpine (\(pid)).") }
        }
    }

    private func installNode(listener: NativeRuntimeInitializationListener) {
        onMain {
            listener.onProgress(phase: "installing_node", detail: "Installing Node 22", fraction: 0.84)
        }
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
                self.forwardSetupOutput(data, listener: listener)
            },
            standardError: { data in
                installOutput.append(String(decoding: data, as: UTF8.self))
                self.forwardSetupOutput(data, listener: listener)
            },
            exit: { code, _ in
                self.verifyNodeAfterInstall(
                    listener: listener,
                    installExitCode: code,
                    installOutput: installOutput
                )
            }
        )
        if pid < 0 {
            onMain { listener.onError(message: "Unable to start Alpine package setup (\(pid)).") }
        }
    }

    private func verifyNodeAfterInstall(
        listener: NativeRuntimeInitializationListener,
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
                if code == 0 {
                    self.markRuntimeReady(listener: listener)
                    return
                }
                let detail = installOutput
                    .trimmingCharacters(in: .whitespacesAndNewlines)
                    .suffix(2_000)
                let suffix = detail.isEmpty ? "" : "\n\(detail)"
                self.onMain {
                    listener.onError(
                        message: "Unable to install Node 22 in Alpine (apk exit \(installExitCode), verification exit \(code)).\(suffix)"
                    )
                }
            }
        )
        if pid < 0 {
            onMain { listener.onError(message: "Unable to verify Node 22 in Alpine (\(pid)).") }
        }
    }

    private static let nodeVerificationCommand =
        "node --version 2>/dev/null | grep -q '^v22\\.' && npm --version >/dev/null 2>&1"

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

    private func markRuntimeReady(listener: NativeRuntimeInitializationListener) {
        operations.async {
            self.initialized = true
            self.onMain {
                listener.onProgress(phase: "ready", detail: "Alpine is ready", fraction: 1.0)
                listener.onReady()
            }
        }
    }

    private func forwardSetupOutput(
        _ data: Data,
        listener: NativeRuntimeInitializationListener
    ) {
        guard !data.isEmpty else { return }
        let text = String(decoding: data, as: UTF8.self)
        onMain { listener.onOutput(text: text) }
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

    func fileExists(path: String, listener: NativeBooleanResultListener) {
        operations.async { [self] in
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
            let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.folder], asCopy: true)
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

    func numberOfPreviewItems(in controller: QLPreviewController) -> Int { previewURL == nil ? 0 : 1 }

    func previewController(_ controller: QLPreviewController, previewItemAt index: Int) -> QLPreviewItem {
        previewURL! as NSURL
    }

    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
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
        if let listener = takeFileExportListener() { listener.onCancelled() }
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
        filePickerListener != nil || filesPickerListener != nil || directoryPickerListener != nil || fileExportListener != nil
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
        let support = try FileManager.default.url(
            for: .applicationSupportDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: true
        )
        let workspace = support.appendingPathComponent("Workspace", isDirectory: true)
        try FileManager.default.createDirectory(at: workspace, withIntermediateDirectories: true)
        return workspace
    }

    private func alpineRuntimeRootURL() throws -> URL {
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
        guard let source = Bundle.main.url(forResource: "bridge", withExtension: "mjs") else {
            throw RuntimeHostError.operationFailed("Bundled Pi Bridge is missing.")
        }
        try guestCreateDirectories("/root/.aether/pi-bridge")
        let bytes = try Data(contentsOf: source)
        try runtime.writeFile(
            "/root/.aether/pi-bridge/bridge.mjs",
            data: bytes,
            executable: false
        )
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

private final class AetherBackgroundExecutionCoordinator {
    static let shared = AetherBackgroundExecutionCoordinator()

    private struct Lease {
        let name: String
        let onExpired: () -> Void
        var detail: String
    }

    private var leases: [String: Lease] = [:]
    private var briefTaskIdentifier: UIBackgroundTaskIdentifier = .invalid
    private var registrationAttempted = false
    private var continuedTask: AnyObject?
    private var continuedTaskSubmitted = false
    private var pendingCompletionSuccess: Bool?
    private var progressTimer: DispatchSourceTimer?
    private var progressActivityCount: Int64 = 0
    private var lastProgressUpdate = Date.distantPast
    private var lastProgressAdvance = Date.distantPast

    private var taskIdentifierPattern: String {
        "\(Bundle.main.bundleIdentifier ?? "com.baimoqilin.aether").agent.*"
    }

    func register() {
        onMainSync {
            guard #available(iOS 26.0, *), !registrationAttempted else { return }
            registrationAttempted = true
            BGTaskScheduler.shared.register(
                forTaskWithIdentifier: taskIdentifierPattern,
                using: nil
            ) { [weak self] task in
                guard let self, let task = task as? BGContinuedProcessingTask else {
                    task.setTaskCompleted(success: false)
                    return
                }
                self.onMain { self.attach(task) }
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
            self?.expireAll()
        }
    }

    @available(iOS 26.0, *)
    private func ensureContinuedProcessingTask(name: String) {
        let scheduler = BGTaskScheduler.shared
        register()
        guard !continuedTaskSubmitted, continuedTask == nil else { return }
        let request = BGContinuedProcessingTaskRequest(
            identifier: taskIdentifierPattern,
            title: name,
            subtitle: "Agent is working"
        )
        request.strategy = .fail
        do {
            try scheduler.submit(request)
            continuedTaskSubmitted = true
        } catch {
            continuedTaskSubmitted = false
        }
    }

    @available(iOS 26.0, *)
    private func attach(_ task: BGContinuedProcessingTask) {
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
