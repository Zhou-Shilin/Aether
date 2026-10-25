import Foundation
import SwiftUI
import UIKit
import AetherShared
import FileProvider

extension Notification.Name {
    static let openAlpineFileManager = Notification.Name("Aether.OpenAlpineFileManager")
}

private final class AetherAppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        IosNativeChatHost.shared.factory = NativeChatFactory.shared
        IosAnalytics.shared.setListener(listener: AetherAnalytics.shared)
        AetherRuntimeHost.shared.registerBackgroundExecution()
        let domain = NSFileProviderDomain(identifier: NSFileProviderDomainIdentifier("com.baimoqilin.aether"), displayName: "Aether")
        NSFileProviderManager.add(domain) { error in
            if let error = error as NSError? {
                NSLog(
                    "Aether File Provider registration failed (%@ %ld): %@",
                    error.domain,
                    error.code,
                    error.localizedDescription
                )
            } else {
                NSLog("Aether File Provider domain registered")
            }
        }
        AetherRuntimeHost.shared.refreshApkRepositoriesForCurrentNetwork()
        return true
    }
}

@main
struct AetherIOSApp: App {
    @UIApplicationDelegateAdaptor(AetherAppDelegate.self) private var appDelegate
    @Environment(\.scenePhase) private var scenePhase
    @State private var presentsAlpineFileManager = false
    @StateObject private var nativeSettings = NativeSettingsModel()
    @StateObject private var workspace = NativeWorkspaceModel()
    @State private var presentsTerminal = false
    @State private var presentsSkills = false
    @State private var presentsScheduledTasks = false

    var body: some Scene {
        WindowGroup {
            if workspace.isDuo {
                NavigationStack {
                    ComposeRootView(workspace: workspace, settings: nativeSettings)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .ignoresSafeArea(.container, edges: .vertical)
                        .toolbar(.hidden, for: .navigationBar)
                        .toolbar {
                            if workspace.chatVisible {
                                ToolbarItemGroup(placement: .bottomBar) {
                                    Button { IosWorkspaceHost.shared.perform(action: "newChat") } label: {
                                        Label(nativeSettings.text("New Chat", "新会话"), systemImage: "square.and.pencil")
                                    }
                                    Button { presentsScheduledTasks = true } label: {
                                        Label { Text(nativeSettings.text("Scheduled Tasks", "定时任务")) } icon: { Image(uiImage: WorkspaceOutlineIcon.clock8Image) }
                                    }
                                    Button { presentsSkills = true } label: {
                                        Label { Text(nativeSettings.text("Skills", "技能")) } icon: { Image(uiImage: WorkspaceOutlineIcon.blocksImage) }
                                    }
                                    Button { presentsAlpineFileManager = true } label: {
                                        Label(nativeSettings.text("Files", "文件"), systemImage: "folder")
                                    }
                                    Button { presentsTerminal = true } label: {
                                        Label(nativeSettings.text("Terminal", "终端"), systemImage: "terminal")
                                    }
                                    Spacer()
                                    Button { nativeSettings.isPresented = true } label: {
                                        Label(nativeSettings.text("Settings", "设置"), systemImage: "gearshape")
                                    }
                                }
                            }
                        }
                }
                    .ignoresSafeArea(.keyboard)
                    .task(id: scenePhase) {
                        while !Task.isCancelled {
                            if scenePhase == .active, workspace.chatVisible { NativeScheduledTaskStore.shared.checkDue() }
                            try? await Task.sleep(for: .seconds(15))
                        }
                    }
                    .onReceive(NotificationCenter.default.publisher(for: .openAlpineFileManager)) { _ in
                        presentsAlpineFileManager = true
                    }
                    .sheet(isPresented: $presentsAlpineFileManager) {
                        DuoAlpineFileManagerView(host: AetherRuntimeHost.shared)
                    }
                    .fullScreenCover(isPresented: $presentsTerminal) { NativeAlpineTerminalScreen() }
                    .sheet(isPresented: $presentsSkills) {
                        NavigationStack {
                            NativeSkillsSettingsView(model: nativeSettings)
                                .toolbar { ToolbarItem(placement: .confirmationAction) {
                                    Button(nativeSettings.text("Done", "完成")) { presentsSkills = false }
                                } }
                        }
                    }
                    .sheet(isPresented: $presentsScheduledTasks) {
                        NativeScheduledTasksView(model: nativeSettings)
                    }
                    .fullScreenCover(isPresented: $nativeSettings.isPresented) {
                        NativeSettingsView(model: nativeSettings, usesDuoLayout: true)
                    }
            } else {
                ComposeRootView(workspace: workspace, settings: nativeSettings)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .ignoresSafeArea()
                    .onReceive(NotificationCenter.default.publisher(for: .openAlpineFileManager)) { _ in
                        presentsAlpineFileManager = true
                    }
                    .sheet(isPresented: $presentsAlpineFileManager) {
                        AlpineFileManagerView(host: AetherRuntimeHost.shared)
                    }
                    .sheet(isPresented: $nativeSettings.isPresented) {
                        NativeSettingsView(model: nativeSettings)
                    }
            }
        }
        .onChange(of: scenePhase) { _, phase in
            switch phase {
            case .background:
                SharedApplicationLifecycle.shared.enterBackground()
            case .active:
                SharedApplicationLifecycle.shared.enterForeground()
            default:
                break
            }
        }
    }
}

@MainActor
private final class NativeWorkspaceModel: NSObject, ObservableObject, @preconcurrency IosWorkspaceListener {
    private weak var currentController: UIViewController?
    private var duoController: UIViewController?
    func makeComposeContent() -> UIViewController {
        if IosWorkspaceHost.shared.duoEnabled, let duoController { return duoController }
        let controller = MainViewControllerKt.MainViewController(runtimeHost: AetherRuntimeHost.shared)
        currentController = controller
        if IosWorkspaceHost.shared.duoEnabled { duoController = controller }
        return controller
    }
    let computer = DuoAgentComputerModel()
    @Published var isDuo = false
    @Published var chatVisible = false
    override init() {
        super.init()
        IosWorkspaceHost.shared.setListener(listener: self)
    }
    func onDuoCapabilityChanged(enabled: Bool) {
        if enabled { duoController = currentController }
        DispatchQueue.main.async { [weak self] in self?.isDuo = enabled }
    }
    func onComputerSnapshotChanged(snapshotJson: String) {
        DispatchQueue.main.async { [weak self] in self?.computer.receive(snapshotJson) }
    }
    func onChatVisibilityChanged(visible: Bool) {
        DispatchQueue.main.async { [weak self] in self?.chatVisible = visible }
    }
}

private struct ComposeRootView: UIViewControllerRepresentable {
    @ObservedObject var workspace: NativeWorkspaceModel
    let settings: NativeSettingsModel
    func makeUIViewController(context: Context) -> UIViewController {
        FullscreenComposeViewController(
            content: workspace.makeComposeContent(), workspace: workspace, settings: settings
        )
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        if workspace.isDuo { uiViewController.view.setNeedsLayout() }
    }
}

private final class FullscreenComposeViewController: UIViewController {
    private let content: UIViewController
    private let workspace: NativeWorkspaceModel
    private let settings: NativeSettingsModel
    private var contentEdges: [NSLayoutConstraint] = []
    private var computerController: UIViewController?
    private let divisionView = UIView()
    private var lastChatFrame: CGRect?
    private var lastComputerFrame: CGRect?
    private var lastComputerVisible = false

    init(content: UIViewController, workspace: NativeWorkspaceModel, settings: NativeSettingsModel) {
        self.content = content
        self.workspace = workspace
        self.settings = settings
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear

        if content.parent != nil {
            content.willMove(toParent: nil)
            content.view.removeFromSuperview()
            content.removeFromParent()
        }
        addChild(content)
        content.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(content.view)
        contentEdges = [
            content.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            content.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            content.view.topAnchor.constraint(equalTo: view.topAnchor),
            content.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ]
        NSLayoutConstraint.activate(contentEdges)
        content.didMove(toParent: self)
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        #if AETHER_DUO_SDK
        if #available(iOS 27.1, *), UIDevice.current.userInterfaceIdiom == .phone {
            // An inactive division still identifies the folding display when fully open.
            // A wide window, landscape iPhone or iPad must never enable the workspace.
            let regions = view.reservedRegions(kind: .division, options: .includeInactive)
            if !regions.isEmpty { IosWorkspaceHost.shared.confirmDuoCapability() }
            guard IosWorkspaceHost.shared.duoEnabled else { return }
            IosWorkspaceHost.shared.updateScene(regularWidth: traitCollection.horizontalSizeClass == .regular)
            let fold = regions.first { $0.isActive }
            IosWorkspaceHost.shared.updateFold(active: fold != nil, x: Float(fold?.frame.minX ?? 0), width: Float(fold?.frame.width ?? 0))
            layoutDuoComputer(activeDivision: fold?.frame)
        }
        #endif
    }

    @available(iOS 18.0, *)
    private func layoutDuoComputer(activeDivision: CGRect?) {
        if computerController == nil {
            // Keep the existing Compose controller: folding never recreates the conversation.
            NSLayoutConstraint.deactivate(contentEdges)
            content.view.translatesAutoresizingMaskIntoConstraints = true
            let controller = UIHostingController(rootView: DuoAgentComputerView(model: workspace.computer, settings: settings))
            controller.safeAreaRegions = .container
            addChild(controller)
            view.addSubview(controller.view)
            controller.didMove(toParent: self)
            controller.view.backgroundColor = .clear
            controller.view.alpha = 0
            controller.view.isHidden = true
            computerController = controller
            divisionView.backgroundColor = .secondarySystemBackground
            divisionView.isUserInteractionEnabled = false
            divisionView.accessibilityElementsHidden = true
            view.addSubview(divisionView)
            view.clipsToBounds = true
        }
        guard let computer = computerController?.view else { return }
        let layout = DuoComputerLayout.resolve(
            bounds: view.bounds, activeDivision: activeDivision,
            isDuo: workspace.isDuo, chatVisible: workspace.chatVisible
        )
        let visible = layout != nil
        workspace.computer.updatePresentation(isDuo: workspace.isDuo, halfFolded: visible, chatVisible: workspace.chatVisible)
        let chatFrame = layout?.chat ?? view.bounds
        let computerFrame = layout?.computer ?? CGRect(
            x: view.bounds.maxX, y: view.bounds.minY,
            width: view.bounds.width / 2, height: view.bounds.height
        )
        guard chatFrame != lastChatFrame || computerFrame != lastComputerFrame || visible != lastComputerVisible else { return }
        let animate = lastChatFrame != nil && !UIAccessibility.isReduceMotionEnabled
        let visibilityChanged = visible != lastComputerVisible
        if visibilityChanged { view.endEditing(true) }
        // Crossfade the old chat geometry while Compose reflows its existing content.
        let snapshot = animate && workspace.chatVisible ? content.view.snapshotView(afterScreenUpdates: false) : nil
        if let snapshot {
            snapshot.frame = content.view.frame
            snapshot.isUserInteractionEnabled = false
            snapshot.accessibilityElementsHidden = true
            view.insertSubview(snapshot, aboveSubview: content.view)
        }
        lastChatFrame = chatFrame
        lastComputerFrame = computerFrame
        lastComputerVisible = visible
        if visible { computer.isHidden = false }
        computer.accessibilityElementsHidden = !visible
        computer.isUserInteractionEnabled = visible
        let changes = {
            self.content.view.frame = chatFrame
            computer.frame = computerFrame
            computer.alpha = visible ? 1 : 0
            self.divisionView.frame = layout?.division ?? CGRect(x: chatFrame.maxX, y: 0, width: 0, height: chatFrame.height)
            self.divisionView.alpha = visible ? 1 : 0
            snapshot?.frame = chatFrame
            snapshot?.alpha = 0
            self.content.view.layoutIfNeeded()
        }
        let completion: (Bool) -> Void = { _ in
            snapshot?.removeFromSuperview()
            if !self.lastComputerVisible { computer.isHidden = true }
        }
        if animate {
            let duration = UIView.inheritedAnimationDuration > 0 ? UIView.inheritedAnimationDuration : 0.32
            UIView.animate(withDuration: duration, delay: 0, options: [.beginFromCurrentState, .curveEaseInOut, .allowUserInteraction], animations: changes, completion: completion)
        } else {
            changes()
            completion(true)
        }
    }

    override func viewWillTransition(
        to size: CGSize,
        with coordinator: any UIViewControllerTransitionCoordinator
    ) {
        // Compose can otherwise retain the keyboard frame from the previous
        // orientation and keep bottom IME insets after the keyboard disappears.
        view.endEditing(true)
        super.viewWillTransition(to: size, with: coordinator)

        coordinator.animate(alongsideTransition: nil) { [weak self] _ in
            self?.content.view.setNeedsLayout()
            self?.content.view.layoutIfNeeded()
        }
    }
}

/// Exact outlines supplied for the Duo design, shared with the Compose Lucide vectors.
struct WorkspaceOutlineIcon: View {
    enum Kind { case clock8, blocks }
    let kind: Kind
    @MainActor static let clock8Image = render(.clock8)
    @MainActor static let blocksImage = render(.blocks)
    @MainActor private static func render(_ kind: Kind) -> UIImage {
        let renderer = ImageRenderer(content: WorkspaceOutlineIcon(kind: kind).foregroundStyle(.black))
        renderer.scale = 3
        return (renderer.uiImage ?? UIImage()).withRenderingMode(.alwaysTemplate)
    }
    var body: some View {
        Path { p in
            switch kind {
            case .clock8:
                p.addEllipse(in: CGRect(x: 2, y: 2, width: 20, height: 20))
                p.move(to: CGPoint(x: 12, y: 6)); p.addLine(to: CGPoint(x: 12, y: 12)); p.addLine(to: CGPoint(x: 8, y: 14))
            case .blocks:
                p.move(to: CGPoint(x: 10, y: 22)); p.addLine(to: CGPoint(x: 10, y: 7))
                p.addQuadCurve(to: CGPoint(x: 9, y: 6), control: CGPoint(x: 10, y: 6))
                p.addLine(to: CGPoint(x: 4, y: 6)); p.addQuadCurve(to: CGPoint(x: 2, y: 8), control: CGPoint(x: 2, y: 6))
                p.addLine(to: CGPoint(x: 2, y: 20)); p.addQuadCurve(to: CGPoint(x: 4, y: 22), control: CGPoint(x: 2, y: 22))
                p.addLine(to: CGPoint(x: 16, y: 22)); p.addQuadCurve(to: CGPoint(x: 18, y: 20), control: CGPoint(x: 18, y: 22))
                p.addLine(to: CGPoint(x: 18, y: 15)); p.addQuadCurve(to: CGPoint(x: 17, y: 14), control: CGPoint(x: 18, y: 14))
                p.addLine(to: CGPoint(x: 2, y: 14))
                p.addRoundedRect(in: CGRect(x: 14, y: 2, width: 8, height: 8), cornerSize: CGSize(width: 1, height: 1))
            }
        }
        .stroke(style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
        .frame(width: 24, height: 24)
        .accessibilityHidden(true)
    }
}

private struct NativeScheduledTask: Codable, Identifiable {
    var id = UUID().uuidString
    var name = ""
    var prompt = ""
    var enabled = true
    var kind = "interval"
    var intervalMinutes = 60
    var hour = 9
    var minute = 0
    var weekdays: Set<Int> = [2, 3, 4, 5, 6]
    var nextRun: Date?
    var lastRun: Date?

    func following(_ date: Date) -> Date? {
        if kind == "interval" { return date.addingTimeInterval(Double(max(1, intervalMinutes)) * 60) }
        let calendar = Calendar.current
        if kind == "daily" {
            return calendar.nextDate(after: date, matching: DateComponents(hour: hour, minute: minute), matchingPolicy: .nextTime)
        }
        return weekdays.compactMap { day in
            calendar.nextDate(after: date, matching: DateComponents(hour: hour, minute: minute, weekday: day), matchingPolicy: .nextTime)
        }.min()
    }
}

@MainActor
private final class NativeScheduledTaskStore: ObservableObject {
    static let shared = NativeScheduledTaskStore()
    @Published var tasks: [NativeScheduledTask] = []
    private let storageKey = "Aether.ScheduledTasks.v1"
    private init() {
        if let data = UserDefaults.standard.data(forKey: storageKey), let saved = try? JSONDecoder().decode([NativeScheduledTask].self, from: data) { tasks = saved }
    }
    func save(_ value: NativeScheduledTask) {
        var task = value
        task.nextRun = task.enabled ? task.following(Date()) : nil
        if let index = tasks.firstIndex(where: { $0.id == task.id }) { tasks[index] = task } else { tasks.append(task) }
        persist()
    }
    func delete(_ offsets: IndexSet) { tasks.remove(atOffsets: offsets); persist() }
    func toggle(_ id: String, enabled: Bool) {
        guard var task = tasks.first(where: { $0.id == id }) else { return }
        task.enabled = enabled; save(task)
    }
    func run(_ id: String) {
        guard let index = tasks.firstIndex(where: { $0.id == id }), IosWorkspaceHost.shared.runScheduledPrompt(taskId: tasks[index].id, title: tasks[index].name, prompt: tasks[index].prompt) else { return }
        tasks[index].lastRun = Date()
        tasks[index].nextRun = tasks[index].enabled ? tasks[index].following(Date()) : nil
        persist()
    }
    func checkDue() {
        let due = tasks.filter { $0.enabled && ($0.nextRun ?? .distantFuture) <= Date() }.map(\.id)
        for id in due { run(id) }
    }
    private func persist() {
        if let data = try? JSONEncoder().encode(tasks) { UserDefaults.standard.set(data, forKey: storageKey) }
    }
}

private struct NativeScheduledTasksView: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var model: NativeSettingsModel
    @ObservedObject private var store = NativeScheduledTaskStore.shared
    @State private var edited: NativeScheduledTask?
    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach(store.tasks) { task in
                        VStack(alignment: .leading, spacing: 8) {
                            Toggle(task.name, isOn: Binding(get: { task.enabled }, set: { store.toggle(task.id, enabled: $0) }))
                            Text(task.prompt).font(.caption).foregroundStyle(.secondary).lineLimit(2)
                            if let next = task.nextRun { Text(next, style: .date).font(.caption) + Text(" ") + Text(next, style: .time).font(.caption) }
                            HStack {
                                Button(model.text("Edit", "编辑")) { edited = task }.buttonStyle(.borderless)
                                Spacer()
                                Button(model.text("Run now", "立即运行")) { store.run(task.id) }.buttonStyle(.borderless)
                            }
                        }
                    }.onDelete(perform: store.delete)
                    if store.tasks.isEmpty { Text(model.text("No scheduled tasks", "暂无定时任务")).foregroundStyle(.secondary) }
                } footer: {
                    Text(model.text("Tasks run while Aether is active. Overdue tasks run when you reopen the app; iOS controls background execution.", "任务在 Aether 活跃时执行；重新打开应用时补跑到期任务。后台执行由 iOS 管理。"))
                }
            }
            .navigationTitle(model.text("Scheduled Tasks", "定时任务"))
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(model.text("Done", "完成")) { dismiss() } }
                ToolbarItem(placement: .primaryAction) { Button { edited = NativeScheduledTask() } label: { Label(model.text("Add", "添加"), systemImage: "plus") } }
            }
            .sheet(item: $edited) { task in NativeScheduledTaskEditor(model: model, task: task) }
        }
    }
}

private struct NativeScheduledTaskEditor: View {
    @Environment(\.dismiss) private var dismiss
    @ObservedObject var model: NativeSettingsModel
    @State var task: NativeScheduledTask
    private var time: Binding<Date> { Binding(get: { Calendar.current.date(from: DateComponents(hour: task.hour, minute: task.minute)) ?? Date() }, set: {
        task.hour = Calendar.current.component(.hour, from: $0); task.minute = Calendar.current.component(.minute, from: $0)
    }) }
    var body: some View {
        NavigationStack {
            Form {
                TextField(model.text("Name", "名称"), text: $task.name)
                Section(model.text("Prompt", "提示词")) { TextEditor(text: $task.prompt).frame(minHeight: 120) }
                Picker(model.text("Schedule", "计划"), selection: $task.kind) {
                    Text(model.text("Interval", "间隔")).tag("interval")
                    Text(model.text("Daily", "每天")).tag("daily")
                    Text(model.text("Weekly", "每周")).tag("weekly")
                }
                if task.kind == "interval" {
                    Stepper(model.text("Every \(task.intervalMinutes) minutes", "每 \(task.intervalMinutes) 分钟"), value: $task.intervalMinutes, in: 1...10080)
                } else {
                    DatePicker(model.text("Time", "时间"), selection: time, displayedComponents: .hourAndMinute)
                    if task.kind == "weekly" {
                        ForEach(1...7, id: \.self) { day in
                            Toggle(Calendar.current.weekdaySymbols[day - 1], isOn: Binding(get: { task.weekdays.contains(day) }, set: {
                                if $0 { task.weekdays.insert(day) } else { task.weekdays.remove(day) }
                            }))
                        }
                    }
                }
            }
            .navigationTitle(model.text("Scheduled Task", "定时任务"))
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button(model.text("Cancel", "取消")) { dismiss() } }
                ToolbarItem(placement: .confirmationAction) { Button(model.text("Save", "保存")) { NativeScheduledTaskStore.shared.save(task); dismiss() }
                    .disabled(task.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || task.prompt.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || task.kind == "weekly" && task.weekdays.isEmpty)
                }
            }
        }
    }
}
