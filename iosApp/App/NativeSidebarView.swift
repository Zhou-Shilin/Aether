import Combine
import SwiftUI
import UIKit
import AetherShared

struct SidebarSessionItem: Identifiable, Equatable {
    enum Indicator: String, Equatable {
        case none
        case working
        case unviewed
    }

    let id: String
    let title: String
    let indicator: Indicator
}

struct SidebarStrings: Equatable {
    var appName = "Aether"
    var chat = "Chat"
    var newChat = "New chat"
    var search = "Search"
    var settings = "Settings"
    var recents = "Recent"
    var noConversations = "No conversations yet."
    var noMatches = "No chats match"
    var rename = "Rename"
    var export = "Export"
    var delete = "Delete"
    var cancel = "Cancel"
    var save = "Save"
    var close = "Close"
    var menu = "Menu"
}

@MainActor
final class NativeSidebarModel: NSObject, ObservableObject, @preconcurrency IosNativeSidebarListener {
    @Published var isVisible = false
    @Published var isDark = false
    @Published var reduceMotion = false
    @Published var isRTL = false
    @Published var selectedSessionId = ""
    @Published var sessions: [SidebarSessionItem] = []
    @Published var strings = SidebarStrings()

    /// Whether the compact (iPhone / narrow Split View) slide-over drawer is open.
    @Published var isCompactOpen = false
    /// Whether the regular (iPad) UISplitViewController primary column is currently shown.
    @Published var isRegularShowing = false
    /// Tracks whether the root split view is currently collapsed to compact width.
    @Published var isCollapsed = true

    @Published var edgeDragTranslation: CGFloat = 0
    @Published var isEdgeDragging = false

    /// Hook installed by `AetherRootSplitViewController` to drive native UISplitViewController show/hide.
    var onToggleRegularSidebar: (() -> Void)?
    var onCloseRegularSidebar: (() -> Void)?

    private var lastReportedLeadingInset: Float = -1
    private var lastReportedObscures = false
    private var lastReportedOpen = false
    private var regularLeadingInset: CGFloat = 0

    override init() {
        super.init()
        IosNativeSidebarHost.shared.setListener(listener: self)
    }

    var isOpen: Bool {
        isCollapsed ? isCompactOpen : isRegularShowing
    }

    func onSnapshotChanged(snapshotJson: String) {
        DispatchQueue.main.async { [weak self] in
            self?.applySnapshot(snapshotJson)
        }
    }

    func onToggleRequested() {
        DispatchQueue.main.async { [weak self] in
            self?.toggle()
        }
    }

    func onCloseRequested() {
        DispatchQueue.main.async { [weak self] in
            self?.close()
        }
    }

    func updateSplitState(collapsed: Bool, regularShowing: Bool, regularLeadingInset: CGFloat) {
        let wasOpen = isOpen
        if isCollapsed != collapsed {
            isCollapsed = collapsed
            if !collapsed && isCompactOpen {
                isCompactOpen = false
                edgeDragTranslation = 0
                isEdgeDragging = false
            }
        }
        if isRegularShowing != regularShowing {
            isRegularShowing = regularShowing
        }
        self.regularLeadingInset = regularLeadingInset
        syncHostPresentation()
        let nowOpen = isOpen
        if wasOpen != nowOpen && isVisible {
            sendAction(nowOpen ? "drawerOpened" : "drawerClosed")
        }
    }

    func toggle() {
        guard isVisible else { return }
        if isCollapsed {
            setCompactOpen(!isCompactOpen)
        } else {
            onToggleRegularSidebar?()
        }
    }

    func close() {
        if isCollapsed {
            setCompactOpen(false)
        } else {
            onCloseRegularSidebar?()
        }
    }

    func setCompactOpen(_ open: Bool, animated: Bool = true) {
        let changed = isCompactOpen != open
        if open {
            dismissKeyboard()
        }
        let apply = {
            self.isCompactOpen = open
            self.edgeDragTranslation = 0
            self.isEdgeDragging = false
        }
        if animated {
            withAnimation(compactDrawerAnimation) { apply() }
        } else {
            apply()
        }
        syncHostPresentation()
        if changed {
            sendAction(open ? "drawerOpened" : "drawerClosed")
        }
    }

    func handleEdgePan(translationX: CGFloat, velocityX: CGFloat, state: UIGestureRecognizer.State, panelWidth: CGFloat) {
        guard isVisible, isCollapsed, !isCompactOpen else { return }
        let directionalTranslation = isRTL ? -translationX : translationX
        let directionalVelocity = isRTL ? -velocityX : velocityX

        switch state {
        case .began:
            dismissKeyboard()
            isEdgeDragging = true
            edgeDragTranslation = max(0, directionalTranslation)
            syncHostPresentation()
        case .changed:
            edgeDragTranslation = max(0, min(panelWidth + 24, directionalTranslation))
        case .ended, .cancelled, .failed:
            let shouldOpen = state == .ended && (directionalTranslation > panelWidth * 0.32 || directionalVelocity > 460)
            if shouldOpen {
                UIImpactFeedbackGenerator(style: .light).impactOccurred()
            }
            setCompactOpen(shouldOpen, animated: true)
        default:
            break
        }
    }

    func selectSession(_ id: String) {
        UISelectionFeedbackGenerator().selectionChanged()
        sendAction("selectSession", payload: ["id": id])
        if isCollapsed {
            setCompactOpen(false)
        }
    }

    func createNewChat() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        sendAction("newChat")
        if isCollapsed {
            setCompactOpen(false)
        }
    }

    func renameSession(id: String, title: String) {
        let trimmed = title.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        sendAction("renameSession", payload: ["id": id, "title": trimmed])
    }

    func exportSession(_ id: String) {
        sendAction("exportSession", payload: ["id": id])
    }

    func deleteSession(_ id: String) {
        sendAction("deleteSession", payload: ["id": id])
    }

    func openSettings() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        if isCollapsed {
            setCompactOpen(false)
        }
        sendAction("openSettings")
    }

    func dismissKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
        sendAction("dismissKeyboard")
    }

    var compactDrawerAnimation: Animation {
        if reduceMotion {
            return .easeOut(duration: 0.16)
        }
        return .spring(response: 0.34, dampingFraction: 0.88)
    }

    func syncHostPresentation() {
        let inset: Float = (isVisible && !isCollapsed && isRegularShowing) ? Float(regularLeadingInset) : 0
        let obscures = isVisible && isCollapsed && (isCompactOpen || isEdgeDragging)
        let open = isVisible && isOpen
        if inset != lastReportedLeadingInset || obscures != lastReportedObscures || open != lastReportedOpen {
            lastReportedLeadingInset = inset
            lastReportedObscures = obscures
            lastReportedOpen = open
            IosNativeSidebarHost.shared.updatePresentation(
                leadingInsetDp: inset,
                obscuresChatControls: obscures,
                isOpen: open
            )
        }
    }

    private func sendAction(_ action: String, payload: [String: Any] = [:]) {
        let json: String
        if payload.isEmpty {
            json = "{}"
        } else if let data = try? JSONSerialization.data(withJSONObject: payload),
                  let text = String(data: data, encoding: .utf8) {
            json = text
        } else {
            json = "{}"
        }
        IosNativeSidebarHost.shared.perform(action: action, payloadJson: json)
    }

    private func applySnapshot(_ snapshotJson: String) {
        guard let data = snapshotJson.data(using: .utf8),
              let dict = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return }

        let visible = dict["visible"] as? Bool ?? false
        if visible != isVisible {
            isVisible = visible
            if !visible && isCollapsed && isCompactOpen {
                isCompactOpen = false
            }
        }
        let dark = dict["dark"] as? Bool ?? false
        if dark != isDark { isDark = dark }

        let reduce = dict["reduceMotion"] as? Bool ?? false
        if reduce != reduceMotion { reduceMotion = reduce }

        let rtl = dict["rtl"] as? Bool ?? false
        if rtl != isRTL { isRTL = rtl }

        let selected = dict["selectedSessionId"] as? String ?? ""
        if selected != selectedSessionId { selectedSessionId = selected }

        if let rawSessions = dict["sessions"] as? [[String: Any]] {
            let parsed = rawSessions.compactMap { row -> SidebarSessionItem? in
                guard let id = row["id"] as? String, !id.isEmpty else { return nil }
                let title = row["title"] as? String ?? ""
                let indicator = SidebarSessionItem.Indicator(rawValue: row["indicator"] as? String ?? "none") ?? .none
                return SidebarSessionItem(id: id, title: title, indicator: indicator)
            }
            if parsed != sessions { sessions = parsed }
        }

        if let rawStrings = dict["strings"] as? [String: Any] {
            var next = SidebarStrings()
            next.appName = rawStrings["appName"] as? String ?? next.appName
            next.chat = rawStrings["chat"] as? String ?? next.chat
            next.newChat = rawStrings["newChat"] as? String ?? next.newChat
            next.search = rawStrings["search"] as? String ?? next.search
            next.settings = rawStrings["settings"] as? String ?? next.settings
            next.recents = rawStrings["recents"] as? String ?? next.recents
            next.noConversations = rawStrings["noConversations"] as? String ?? next.noConversations
            next.noMatches = rawStrings["noMatches"] as? String ?? next.noMatches
            next.rename = rawStrings["rename"] as? String ?? next.rename
            next.export = rawStrings["export"] as? String ?? next.export
            next.delete = rawStrings["delete"] as? String ?? next.delete
            next.cancel = rawStrings["cancel"] as? String ?? next.cancel
            next.save = rawStrings["save"] as? String ?? next.save
            next.close = rawStrings["close"] as? String ?? next.close
            next.menu = rawStrings["menu"] as? String ?? next.menu
            if next != strings { strings = next }
        }

        syncHostPresentation()
    }
}

enum NativeSidebarMetrics {
    static let regularPanelWidth: CGFloat = 320
    static let outerLeadingInset: CGFloat = 10
    static let compactCornerRadius: CGFloat = 34
    static let rowHeight: CGFloat = 44
    static let aetherPurple = Color(red: 0.57, green: 0.34, blue: 0.96)
    static let aetherPurpleFallback = Color(red: 0.678, green: 0.482, blue: 0.976)
}

// MARK: - Native UISplitViewController Root (iPadOS 26 Floating Liquid Glass Sidebar + iPhone Slide-Over)

/// Uses Apple's native `UISplitViewController(style: .doubleColumn)` with `.sidebar` primary style
/// on iPad regular widths (for authentic iPadOS 26 floating Liquid Glass sidebar chrome, physics,
/// and transitions), and a dedicated `.compact` slide-over drawer host on iPhone / narrow Split View.
@MainActor
final class AetherRootSplitViewController: UISplitViewController, UISplitViewControllerDelegate {
    private let composeVC: UIViewController
    private let sidebar: NativeSidebarModel

    private let primaryHostingVC: UIHostingController<NativeSplitSidebarRootView>
    private let primaryNavVC: UINavigationController
    private let secondaryHostVC = ComposeContainerHostViewController()
    private let secondaryNavVC: UINavigationController
    private let compactHostVC: NativeCompactDrawerHostViewController

    private var cancellables: Set<AnyCancellable> = []
    private var userWantsRegularSidebarOpen = true
    private var hasAppeared = false

    init(composeVC: UIViewController, sidebar: NativeSidebarModel) {
        self.composeVC = composeVC
        self.sidebar = sidebar

        let primaryRoot = NativeSplitSidebarRootView(model: sidebar)
        let hosting = UIHostingController(rootView: primaryRoot)
        hosting.view.backgroundColor = .clear
        self.primaryHostingVC = hosting

        let primaryNav = UINavigationController(rootViewController: hosting)
        primaryNav.setNavigationBarHidden(true, animated: false)
        primaryNav.view.backgroundColor = .clear
        self.primaryNavVC = primaryNav

        let secondaryNav = UINavigationController(rootViewController: secondaryHostVC)
        secondaryNav.setNavigationBarHidden(true, animated: false)
        self.secondaryNavVC = secondaryNav

        self.compactHostVC = NativeCompactDrawerHostViewController(sidebar: sidebar)

        super.init(style: .doubleColumn)
        self.delegate = self

        // Native iPadOS 26 floating Liquid Glass sidebar configuration.
        self.primaryBackgroundStyle = .sidebar
        self.preferredSplitBehavior = .tile
        self.preferredDisplayMode = .secondaryOnly
        self.preferredPrimaryColumnWidth = NativeSidebarMetrics.regularPanelWidth
        self.minimumPrimaryColumnWidth = NativeSidebarMetrics.regularPanelWidth
        self.maximumPrimaryColumnWidth = NativeSidebarMetrics.regularPanelWidth
        // Disable horizontal swipe-to-close on iPad; toggle is driven by the top-bar glass button.
        self.presentsWithGesture = false
        self.displayModeButtonVisibility = .never

        self.setViewController(primaryNav, for: .primary)
        self.setViewController(secondaryNav, for: .secondary)
        self.setViewController(compactHostVC, for: .compact)

        secondaryHostVC.onSafeAreaChanged = { [weak self] in
            self?.syncSplitPresentation()
        }

        sidebar.onToggleRegularSidebar = { [weak self] in
            self?.toggleRegularSidebar()
        }
        sidebar.onCloseRegularSidebar = { [weak self] in
            self?.setRegularSidebarVisible(false, animated: true)
        }
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear
        attachComposeToActiveHost()
        observeSidebarModel()
        applyThemeAndDirection()
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        hasAppeared = true
        applyDesiredRegularDisplayMode(animated: false)
        syncSplitPresentation()
    }

    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        attachComposeToActiveHost()
        syncSplitPresentation()
    }

    private func observeSidebarModel() {
        sidebar.$isVisible
            .removeDuplicates()
            .sink { [weak self] visible in
                guard let self else { return }
                self.applyDesiredRegularDisplayMode(visible: visible, animated: self.hasAppeared)
                self.syncSplitPresentation(visibleOverride: visible)
            }
            .store(in: &cancellables)

        sidebar.$isDark
            .removeDuplicates()
            .sink { [weak self] isDark in
                self?.applyThemeAndDirection(isDark: isDark, isRTL: self?.sidebar.isRTL ?? false)
            }
            .store(in: &cancellables)

        sidebar.$isRTL
            .removeDuplicates()
            .sink { [weak self] isRTL in
                self?.applyThemeAndDirection(isDark: self?.sidebar.isDark ?? false, isRTL: isRTL)
                self?.syncSplitPresentation()
            }
            .store(in: &cancellables)
    }

    private func applyThemeAndDirection(isDark: Bool? = nil, isRTL: Bool? = nil) {
        let dark = isDark ?? sidebar.isDark
        let rtl = isRTL ?? sidebar.isRTL
        let style: UIUserInterfaceStyle = dark ? .dark : .light
        if overrideUserInterfaceStyle != style {
            overrideUserInterfaceStyle = style
        }
        let semantic: UISemanticContentAttribute = rtl ? .forceRightToLeft : .forceLeftToRight
        if view.semanticContentAttribute != semantic {
            view.semanticContentAttribute = semantic
        }
    }

    private func toggleRegularSidebar() {
        guard !isCollapsed, sidebar.isVisible else { return }
        let currentlyShowing = isPrimaryCurrentlyShown
        setRegularSidebarVisible(!currentlyShowing, animated: !sidebar.reduceMotion)
    }

    private func setRegularSidebarVisible(_ show: Bool, animated: Bool) {
        userWantsRegularSidebarOpen = show
        guard !isCollapsed else { return }
        let targetVisible = show && sidebar.isVisible
        if animated {
            preferredDisplayMode = targetVisible ? .oneBesideSecondary : .secondaryOnly
            if targetVisible {
                self.show(.primary)
            } else {
                self.hide(.primary)
            }
        } else {
            UIView.performWithoutAnimation {
                preferredDisplayMode = targetVisible ? .oneBesideSecondary : .secondaryOnly
                view.layoutIfNeeded()
            }
        }
        syncSplitPresentation()
    }

    private func applyDesiredRegularDisplayMode(visible: Bool? = nil, animated: Bool) {
        guard !isCollapsed else { return }
        let isVis = visible ?? sidebar.isVisible
        let shouldShow = isVis && userWantsRegularSidebarOpen
        let currentlyShowing = isPrimaryCurrentlyShown
        guard shouldShow != currentlyShowing else { return }
        if animated && !sidebar.reduceMotion {
            preferredDisplayMode = shouldShow ? .oneBesideSecondary : .secondaryOnly
            if shouldShow {
                show(.primary)
            } else {
                hide(.primary)
            }
        } else {
            UIView.performWithoutAnimation {
                preferredDisplayMode = shouldShow ? .oneBesideSecondary : .secondaryOnly
                view.layoutIfNeeded()
            }
        }
    }

    private var isPrimaryCurrentlyShown: Bool {
        if #available(iOS 26.0, *) {
            return isShowing(.primary)
        }
        return displayMode == .oneBesideSecondary || displayMode == .oneOverSecondary
    }

    private func attachComposeToActiveHost() {
        let target: ComposeHostingContainer = isCollapsed ? compactHostVC : secondaryHostVC
        target.embedComposeIfNeeded(composeVC)
    }

    private func syncSplitPresentation(visibleOverride: Bool? = nil) {
        let collapsed = isCollapsed
        let isVis = visibleOverride ?? sidebar.isVisible
        let regularShowing = !collapsed && isVis && isPrimaryCurrentlyShown
        // On iPadOS 26, if the floating sidebar overlays the secondary column's leading edge,
        // UIKit reports that occupied width via secondaryHostVC.view.safeAreaInsets.
        // If UIKit tiles the secondary column beside the sidebar, safeAreaInsets.left is 0.
        let safeInsets = secondaryHostVC.view.safeAreaInsets
        let leadingSafe = sidebar.isRTL ? safeInsets.right : safeInsets.left
        sidebar.updateSplitState(
            collapsed: collapsed,
            regularShowing: regularShowing,
            regularLeadingInset: regularShowing ? leadingSafe : 0
        )
    }

    // MARK: UISplitViewControllerDelegate

    func splitViewControllerDidCollapse(_ svc: UISplitViewController) {
        attachComposeToActiveHost()
        syncSplitPresentation()
    }

    func splitViewControllerDidExpand(_ svc: UISplitViewController) {
        attachComposeToActiveHost()
        applyDesiredRegularDisplayMode(animated: false)
        syncSplitPresentation()
    }

    func splitViewController(_ svc: UISplitViewController, willShow column: UISplitViewController.Column) {
        if column == .primary {
            DispatchQueue.main.async { [weak self] in
                self?.syncSplitPresentation()
            }
        }
    }

    func splitViewController(_ svc: UISplitViewController, willHide column: UISplitViewController.Column) {
        if column == .primary {
            DispatchQueue.main.async { [weak self] in
                self?.syncSplitPresentation()
            }
        }
    }

    @available(iOS 26.0, *)
    func splitViewController(_ svc: UISplitViewController, didShow column: UISplitViewController.Column) {
        if column == .primary {
            syncSplitPresentation()
        }
    }

    @available(iOS 26.0, *)
    func splitViewController(_ svc: UISplitViewController, didHide column: UISplitViewController.Column) {
        if column == .primary {
            syncSplitPresentation()
        }
    }
}

@MainActor
private protocol ComposeHostingContainer: UIViewController {
    func embedComposeIfNeeded(_ composeVC: UIViewController)
}

/// Secondary column host for regular-width iPad layouts.
private final class ComposeContainerHostViewController: UIViewController, ComposeHostingContainer {
    var onSafeAreaChanged: (() -> Void)?

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear
    }

    override func viewSafeAreaInsetsDidChange() {
        super.viewSafeAreaInsetsDidChange()
        onSafeAreaChanged?()
    }

    func embedComposeIfNeeded(_ composeVC: UIViewController) {
        guard composeVC.parent !== self else { return }
        if composeVC.parent != nil {
            composeVC.willMove(toParent: nil)
            composeVC.view.removeFromSuperview()
            composeVC.removeFromParent()
        }
        addChild(composeVC)
        composeVC.view.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(composeVC.view)
        NSLayoutConstraint.activate([
            composeVC.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            composeVC.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            composeVC.view.topAnchor.constraint(equalTo: view.topAnchor),
            composeVC.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        composeVC.didMove(toParent: self)
    }
}

/// Compact-width (iPhone & narrow iPad Split View) host combining the Compose chat view
/// with the native SwiftUI slide-over Liquid Glass drawer and edge-pan gesture.
private final class NativeCompactDrawerHostViewController: UIViewController, ComposeHostingContainer, UIGestureRecognizerDelegate {
    private let sidebar: NativeSidebarModel
    private let overlayHostingVC: UIHostingController<NativeCompactSidebarOverlayView>
    private var leftEdgePan: UIScreenEdgePanGestureRecognizer?
    private var rightEdgePan: UIScreenEdgePanGestureRecognizer?
    private var cancellables: Set<AnyCancellable> = []

    init(sidebar: NativeSidebarModel) {
        self.sidebar = sidebar
        let overlay = UIHostingController(rootView: NativeCompactSidebarOverlayView(model: sidebar))
        overlay.view.backgroundColor = .clear
        self.overlayHostingVC = overlay
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = .clear

        addChild(overlayHostingVC)
        overlayHostingVC.view.translatesAutoresizingMaskIntoConstraints = false
        overlayHostingVC.view.isUserInteractionEnabled = false
        overlayHostingVC.view.accessibilityElementsHidden = true
        view.addSubview(overlayHostingVC.view)
        NSLayoutConstraint.activate([
            overlayHostingVC.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            overlayHostingVC.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            overlayHostingVC.view.topAnchor.constraint(equalTo: view.topAnchor),
            overlayHostingVC.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        overlayHostingVC.didMove(toParent: self)

        Publishers.CombineLatest3(sidebar.$isVisible, sidebar.$isCompactOpen, sidebar.$isEdgeDragging)
            .sink { [weak self] visible, open, dragging in
                let active = visible && (open || dragging)
                self?.overlayHostingVC.view.isUserInteractionEnabled = active
                self?.overlayHostingVC.view.accessibilityElementsHidden = !active
            }
            .store(in: &cancellables)

        let left = UIScreenEdgePanGestureRecognizer(target: self, action: #selector(handleEdgePan(_:)))
        left.edges = .left
        left.delegate = self
        left.cancelsTouchesInView = false
        view.addGestureRecognizer(left)
        leftEdgePan = left

        let right = UIScreenEdgePanGestureRecognizer(target: self, action: #selector(handleEdgePan(_:)))
        right.edges = .right
        right.delegate = self
        right.cancelsTouchesInView = false
        view.addGestureRecognizer(right)
        rightEdgePan = right
    }

    func embedComposeIfNeeded(_ composeVC: UIViewController) {
        guard composeVC.parent !== self else { return }
        if composeVC.parent != nil {
            composeVC.willMove(toParent: nil)
            composeVC.view.removeFromSuperview()
            composeVC.removeFromParent()
        }
        addChild(composeVC)
        composeVC.view.translatesAutoresizingMaskIntoConstraints = false
        view.insertSubview(composeVC.view, belowSubview: overlayHostingVC.view)
        NSLayoutConstraint.activate([
            composeVC.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            composeVC.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            composeVC.view.topAnchor.constraint(equalTo: view.topAnchor),
            composeVC.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        composeVC.didMove(toParent: self)
    }

    func gestureRecognizerShouldBegin(_ gestureRecognizer: UIGestureRecognizer) -> Bool {
        guard sidebar.isVisible, sidebar.isCollapsed, !sidebar.isCompactOpen else { return false }
        if gestureRecognizer === leftEdgePan {
            return !sidebar.isRTL
        }
        if gestureRecognizer === rightEdgePan {
            return sidebar.isRTL
        }
        return true
    }

    @objc private func handleEdgePan(_ gesture: UIScreenEdgePanGestureRecognizer) {
        let width = view.bounds.width
        let panelWidth = min(NativeSidebarMetrics.regularPanelWidth, max(268, width - 54))
        let translation = gesture.translation(in: view)
        let velocity = gesture.velocity(in: view)
        sidebar.handleEdgePan(
            translationX: translation.x,
            velocityX: velocity.x,
            state: gesture.state,
            panelWidth: panelWidth
        )
    }
}

// MARK: - iPad Split View Primary Column Content

private struct NativeSplitSidebarRootView: View {
    @ObservedObject var model: NativeSidebarModel

    var body: some View {
        NativeSidebarColumnContent(model: model, panelDragGesture: nil)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color.clear)
            .preferredColorScheme(model.isDark ? .dark : .light)
            .environment(\.layoutDirection, model.isRTL ? .rightToLeft : .leftToRight)
    }
}

// MARK: - iPhone / Compact Slide-Over Floating Liquid Glass Drawer

private struct NativeCompactSidebarOverlayView: View {
    @ObservedObject var model: NativeSidebarModel
    @State private var dragOffsetX: CGFloat = 0
    @State private var isDismissDragging = false

    var body: some View {
        GeometryReader { proxy in
            let panelWidth = min(NativeSidebarMetrics.regularPanelWidth, max(268, proxy.size.width - 54))
            let topInset = max(proxy.safeAreaInsets.top + 2, 16)
            let bottomInset = max(proxy.safeAreaInsets.bottom - 16, 12)
            let hiddenX = -(panelWidth + NativeSidebarMetrics.outerLeadingInset + 24)

            let effectiveX: CGFloat = {
                if model.isCompactOpen {
                    if isDismissDragging {
                        return dragOffsetX > 0
                            ? rubberBand(dragOffsetX, limit: 18)
                            : max(hiddenX, dragOffsetX)
                    }
                    return 0
                } else if model.isEdgeDragging {
                    let progress = min(1.0, max(0.0, model.edgeDragTranslation / panelWidth))
                    return hiddenX * (1.0 - progress)
                } else {
                    return hiddenX
                }
            }()
            let openProgress: CGFloat = max(0, min(1, 1.0 - (abs(min(0, effectiveX)) / abs(hiddenX))))
            let panelVisible = model.isVisible && (model.isCompactOpen || model.isEdgeDragging || isDismissDragging)
            let shape = RoundedRectangle(cornerRadius: NativeSidebarMetrics.compactCornerRadius, style: .continuous)

            ZStack(alignment: .leading) {
                if model.isVisible && openProgress > 0.001 {
                    Color.black
                        .opacity((model.isDark ? 0.46 : 0.18) * openProgress)
                        .ignoresSafeArea()
                        .onTapGesture {
                            model.setCompactOpen(false)
                        }
                        .gesture(dismissDragGesture(panelWidth: panelWidth))
                        .accessibilityHidden(true)

                    if model.isCompactOpen {
                        HStack(spacing: 0) {
                            Color.clear
                                .frame(width: panelWidth + NativeSidebarMetrics.outerLeadingInset)
                                .allowsHitTesting(false)
                                .accessibilityHidden(true)
                            Color.clear
                                .contentShape(Rectangle())
                                .frame(maxWidth: .infinity, maxHeight: .infinity)
                                .onTapGesture {
                                    model.setCompactOpen(false)
                                }
                                .gesture(dismissDragGesture(panelWidth: panelWidth))
                                .accessibilityElement(children: .ignore)
                                .accessibilityAddTraits(.isButton)
                                .accessibilityLabel(model.strings.close)
                        }
                        .ignoresSafeArea()
                    }
                }

                if model.isVisible {
                    ZStack {
                        CompactSidebarGlassBackground(shape: shape, isDark: model.isDark)
                            .allowsHitTesting(false)

                        NativeSidebarColumnContent(
                            model: model,
                            panelDragGesture: dismissDragGesture(panelWidth: panelWidth)
                        )
                        .clipShape(shape)
                    }
                    .frame(width: panelWidth)
                    .padding(.leading, NativeSidebarMetrics.outerLeadingInset)
                    .padding(.top, topInset)
                    .padding(.bottom, bottomInset)
                    .offset(x: effectiveX)
                    .opacity(panelVisible ? Double(min(1, openProgress * 1.35 + 0.05)) : 0)
                    .allowsHitTesting(model.isCompactOpen)
                    .accessibilityHidden(!model.isCompactOpen)
                    .animation(isDismissDragging || model.isEdgeDragging ? nil : model.compactDrawerAnimation, value: model.isCompactOpen)
                    .animation(isDismissDragging ? nil : model.compactDrawerAnimation, value: dragOffsetX)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            .ignoresSafeArea()
        }
        .ignoresSafeArea(.keyboard)
        .preferredColorScheme(model.isDark ? .dark : .light)
        .environment(\.layoutDirection, model.isRTL ? .rightToLeft : .leftToRight)
    }

    private func rubberBand(_ delta: CGFloat, limit: CGFloat) -> CGFloat {
        guard delta > 0, limit > 0 else { return 0 }
        return (1.0 - (1.0 / ((delta * 0.55 / limit) + 1.0))) * limit
    }

    private func dismissDragGesture(panelWidth: CGFloat) -> AnyGesture<DragGesture.Value> {
        AnyGesture(
            DragGesture(minimumDistance: 12, coordinateSpace: .global)
                .onChanged { value in
                    guard model.isCompactOpen else { return }
                    let dx = model.isRTL ? -value.translation.width : value.translation.width
                    let dy = value.translation.height
                    if !isDismissDragging {
                        guard abs(dx) > abs(dy) * 1.15 else { return }
                        isDismissDragging = true
                    }
                    dragOffsetX = dx
                }
                .onEnded { value in
                    guard isDismissDragging else { return }
                    let dx = model.isRTL ? -value.translation.width : value.translation.width
                    let predictedDx = model.isRTL ? -value.predictedEndTranslation.width : value.predictedEndTranslation.width
                    let shouldClose = dx < -(panelWidth * 0.30) || predictedDx < -(panelWidth * 0.55)
                    isDismissDragging = false
                    if shouldClose {
                        dragOffsetX = 0
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        model.setCompactOpen(false)
                    } else {
                        withAnimation(model.compactDrawerAnimation) {
                            dragOffsetX = 0
                        }
                    }
                }
        )
    }
}

// MARK: - Shared Sidebar Column Content (Header + Scroll List + Floating Bottom Bar)

private struct NativeSidebarColumnContent: View {
    @ObservedObject var model: NativeSidebarModel
    let panelDragGesture: AnyGesture<DragGesture.Value>?

    @State private var isSearching = false
    @State private var searchQuery = ""
    @FocusState private var searchFieldFocused: Bool

    @State private var renamingSession: SidebarSessionItem?
    @State private var renameDraft = ""

    private var hasSearchQuery: Bool {
        !searchQuery.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    private var filteredSessions: [SidebarSessionItem] {
        let query = searchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !query.isEmpty else { return model.sessions }
        return model.sessions.filter { session in
            let displayTitle = session.title.isEmpty ? model.strings.newChat : session.title
            return displayTitle.localizedCaseInsensitiveContains(query)
        }
    }

    var body: some View {
        contentContainer
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .onChange(of: model.isOpen) { _, open in
                if !open && (isSearching || !searchQuery.isEmpty) {
                    dismissSearch(animated: false)
                }
            }
            .alert(
                model.strings.rename,
                isPresented: Binding(
                    get: { renamingSession != nil },
                    set: { if !$0 { renamingSession = nil } }
                )
            ) {
                TextField(model.strings.newChat, text: $renameDraft)
                Button(model.strings.cancel, role: .cancel) {
                    renamingSession = nil
                }
                Button(model.strings.save) {
                    if let session = renamingSession {
                        model.renameSession(id: session.id, title: renameDraft)
                    }
                    renamingSession = nil
                }
            }
    }

    @ViewBuilder
    private var contentContainer: some View {
        let scrollWithGesture = sessionScrollList
            .modifier(OptionalSimultaneousDragModifier(gesture: panelDragGesture))

        if #available(iOS 26.0, *) {
            // Native iOS 26 safeAreaBar + soft scroll edge effect gives the exact progressive
            // Gaussian blur & frosted fade behind both the top header and bottom floating controls.
            scrollWithGesture
                .safeAreaBar(edge: .top, spacing: 0) {
                    topHeaderBar
                }
                .safeAreaBar(edge: .bottom, spacing: 0) {
                    bottomFloatingBar
                }
                .scrollEdgeEffectStyle(.soft, for: .all)
        } else {
            scrollWithGesture
                .safeAreaPadding(.top, 68)
                .safeAreaPadding(.bottom, 86)
                .overlay(alignment: .top) {
                    topHeaderBar
                }
                .overlay(alignment: .bottom) {
                    bottomFloatingBar
                }
        }
    }

    @ViewBuilder
    private var sessionScrollList: some View {
        let scroll = ScrollView(.vertical, showsIndicators: false) {
            LazyVStack(alignment: .leading, spacing: 2) {
                if !filteredSessions.isEmpty {
                    Text(model.strings.recents)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(.primary.opacity(model.isDark ? 0.85 : 0.82))
                        .padding(.horizontal, 22)
                        .padding(.top, 10)
                        .padding(.bottom, 6)
                }

                if filteredSessions.isEmpty {
                    Text(hasSearchQuery ? model.strings.noMatches : model.strings.noConversations)
                        .font(.system(size: 15))
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 22)
                        .padding(.vertical, 14)
                } else {
                    ForEach(filteredSessions) { session in
                        SidebarSessionRow(
                            session: session,
                            fallbackTitle: model.strings.newChat,
                            isSelected: session.id == model.selectedSessionId,
                            isDark: model.isDark,
                            strings: model.strings,
                            onSelect: {
                                dismissSearch(animated: true)
                                model.selectSession(session.id)
                            },
                            onRename: {
                                renameDraft = session.title.isEmpty ? model.strings.newChat : session.title
                                renamingSession = session
                            },
                            onExport: {
                                model.exportSession(session.id)
                            },
                            onDelete: {
                                model.deleteSession(session.id)
                            }
                        )
                    }
                }
            }
            .padding(.bottom, 12)
        }
        .scrollDismissesKeyboard(.interactively)

        if #available(iOS 26.0, *) {
            scroll
        } else {
            scroll
                .mask(
                    VStack(spacing: 0) {
                        LinearGradient(colors: [.clear, .black], startPoint: .top, endPoint: .bottom)
                            .frame(height: 22)
                        Color.black
                        LinearGradient(colors: [.black, .clear], startPoint: .top, endPoint: .bottom)
                            .frame(height: 28)
                    }
                )
        }
    }

    private var topHeaderBar: some View {
        HStack(spacing: 10) {
            if isSearching {
                searchFieldPill
                    .transition(.opacity.combined(with: .scale(scale: 0.96, anchor: .trailing)))

                Button {
                    dismissSearch(animated: true)
                } label: {
                    Image(systemName: "xmark")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(.primary)
                        .frame(width: 44, height: 44)
                        .contentShape(Circle())
                }
                .buttonStyle(SidebarHeaderCircleGlassButtonStyle(isDark: model.isDark))
                .accessibilityLabel(model.strings.close)
                .accessibilityIdentifier("sidebar.searchClose")
            } else {
                Text(model.strings.appName)
                    .font(.system(size: 21, weight: .semibold))
                    .tracking(-0.2)
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                    .padding(.leading, 6)

                Spacer(minLength: 8)

                Button {
                    openSearch()
                } label: {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 17, weight: .medium))
                        .foregroundStyle(.primary)
                        .frame(width: 44, height: 44)
                        .contentShape(Circle())
                }
                .buttonStyle(SidebarHeaderCircleGlassButtonStyle(isDark: model.isDark))
                .accessibilityLabel(model.strings.search)
                .accessibilityIdentifier("sidebar.search")
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 14)
        .padding(.bottom, 8)
    }

    private var searchFieldPill: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(.secondary)

            TextField(model.strings.search, text: $searchQuery)
                .font(.system(size: 15.5))
                .foregroundStyle(.primary)
                .focused($searchFieldFocused)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .submitLabel(.search)
                .accessibilityIdentifier("sidebar.searchField")

            if !searchQuery.isEmpty {
                Button {
                    searchQuery = ""
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 15))
                        .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 14)
        .frame(height: 44)
        .frame(maxWidth: .infinity)
        .modifier(SidebarSearchCapsuleModifier(isDark: model.isDark))
    }

    private var bottomFloatingBar: some View {
        HStack(spacing: 12) {
            Button {
                dismissSearch(animated: true)
                model.createNewChat()
            } label: {
                HStack(spacing: 8) {
                    Image(systemName: "square.and.pencil")
                        .font(.system(size: 17, weight: .semibold))
                    Text(model.strings.chat)
                        .font(.system(size: 16, weight: .semibold))
                        .lineLimit(1)
                }
                .foregroundStyle(.white)
                .padding(.horizontal, 18)
                .frame(height: 48)
                .contentShape(Capsule())
            }
            .buttonStyle(SidebarProminentGlassCapsuleStyle(isDark: model.isDark))
            .accessibilityLabel(model.strings.chat)
            .accessibilityIdentifier("sidebar.newChat")

            Spacer(minLength: 8)

            Button {
                dismissSearch(animated: true)
                model.openSettings()
            } label: {
                Image(systemName: "gearshape")
                    .font(.system(size: 19, weight: .medium))
                    .foregroundStyle(.primary)
                    .frame(width: 48, height: 48)
                    .contentShape(Circle())
            }
            .buttonStyle(SidebarCircleGlassButtonStyle(isDark: model.isDark))
            .accessibilityLabel(model.strings.settings)
            .accessibilityIdentifier("sidebar.settings")
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
        .padding(.bottom, 18)
    }

    private func openSearch() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        withAnimation(model.reduceMotion ? .easeOut(duration: 0.14) : .spring(response: 0.34, dampingFraction: 0.84)) {
            isSearching = true
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.08) {
            searchFieldFocused = true
        }
    }

    private func dismissSearch(animated: Bool) {
        searchFieldFocused = false
        let reset = {
            isSearching = false
            searchQuery = ""
        }
        if animated {
            withAnimation(model.reduceMotion ? .easeOut(duration: 0.14) : .spring(response: 0.32, dampingFraction: 0.86)) {
                reset()
            }
        } else {
            reset()
        }
    }
}

private struct OptionalSimultaneousDragModifier: ViewModifier {
    let gesture: AnyGesture<DragGesture.Value>?

    func body(content: Content) -> some View {
        if let gesture {
            content.simultaneousGesture(gesture)
        } else {
            content
        }
    }
}

private struct SidebarSessionRow: View {
    let session: SidebarSessionItem
    let fallbackTitle: String
    let isSelected: Bool
    let isDark: Bool
    let strings: SidebarStrings
    let onSelect: () -> Void
    let onRename: () -> Void
    let onExport: () -> Void
    let onDelete: () -> Void

    private var displayTitle: String {
        session.title.isEmpty ? fallbackTitle : session.title
    }

    var body: some View {
        Button(action: onSelect) {
            HStack(spacing: 10) {
                Text(displayTitle)
                    .font(.system(size: 15.5, weight: isSelected ? .semibold : .medium))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                    .truncationMode(.tail)
                    .frame(maxWidth: .infinity, alignment: .leading)

                if session.indicator != .none {
                    SidebarSessionStatusDot(indicator: session.indicator)
                }
            }
            .padding(.horizontal, 12)
            .frame(height: NativeSidebarMetrics.rowHeight)
            .background {
                if isSelected {
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(Color.primary.opacity(isDark ? 0.12 : 0.055))
                }
            }
            .contentShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        }
        .buttonStyle(SidebarRowButtonStyle())
        .hoverEffect(.highlight)
        .padding(.horizontal, 10)
        .contextMenu {
            Button(action: onRename) {
                Label(strings.rename, systemImage: "pencil")
            }
            Button(action: onExport) {
                Label(strings.export, systemImage: "square.and.arrow.up")
            }
            Divider()
            Button(role: .destructive, action: onDelete) {
                Label(strings.delete, systemImage: "trash")
            }
        }
    }
}

private struct SidebarRowButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background {
                if configuration.isPressed {
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(Color.primary.opacity(0.06))
                }
            }
            .scaleEffect(configuration.isPressed ? 0.985 : 1.0)
            .animation(.easeOut(duration: 0.14), value: configuration.isPressed)
    }
}

private struct SidebarSessionStatusDot: View {
    let indicator: SidebarSessionItem.Indicator
    @State private var pulse = false

    var body: some View {
        let color = indicator == .working
            ? Color(red: 0.133, green: 0.773, blue: 0.369)
            : Color(red: 0.231, green: 0.510, blue: 0.965)
        Circle()
            .fill(color)
            .frame(width: 8, height: 8)
            .scaleEffect(indicator == .working && pulse ? 1.18 : 1.0)
            .opacity(indicator == .working && pulse ? 0.65 : 1.0)
            .onAppear {
                guard indicator == .working else { return }
                withAnimation(.easeInOut(duration: 0.95).repeatForever(autoreverses: true)) {
                    pulse = true
                }
            }
    }
}

private struct CompactSidebarGlassBackground: View {
    let shape: RoundedRectangle
    let isDark: Bool

    var body: some View {
        if #available(iOS 26.0, *) {
            Color.clear
                .glassEffect(.regular, in: shape)
                .shadow(
                    color: Color.black.opacity(isDark ? 0.45 : 0.10),
                    radius: 24,
                    x: 0,
                    y: 8
                )
        } else {
            Color.clear
                .background(.ultraThinMaterial, in: shape)
                .overlay(
                    shape
                        .strokeBorder(Color.primary.opacity(isDark ? 0.14 : 0.08), lineWidth: 0.5)
                )
                .shadow(
                    color: Color.black.opacity(isDark ? 0.45 : 0.12),
                    radius: 24,
                    x: 0,
                    y: 8
                )
        }
    }
}

private struct SidebarHeaderCircleGlassButtonStyle: ButtonStyle {
    let isDark: Bool

    func makeBody(configuration: Configuration) -> some View {
        if #available(iOS 26.0, *) {
            configuration.label
                .glassEffect(.regular.interactive(), in: .circle)
        } else {
            configuration.label
                .background(
                    Circle()
                        .fill(Color.primary.opacity(isDark ? 0.12 : 0.06))
                )
                .scaleEffect(configuration.isPressed ? 0.93 : 1.0)
                .animation(.spring(response: 0.24, dampingFraction: 0.78), value: configuration.isPressed)
        }
    }
}

private struct SidebarSearchCapsuleModifier: ViewModifier {
    let isDark: Bool

    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content
                .glassEffect(.regular.interactive(), in: .capsule)
        } else {
            content
                .background(
                    Capsule()
                        .fill(Color.primary.opacity(isDark ? 0.12 : 0.06))
                )
        }
    }
}

private struct SidebarCircleGlassButtonStyle: ButtonStyle {
    let isDark: Bool

    func makeBody(configuration: Configuration) -> some View {
        if #available(iOS 26.0, *) {
            configuration.label
                .glassEffect(.regular.interactive(), in: .circle)
                .shadow(color: .black.opacity(isDark ? 0.34 : 0.08), radius: 10, x: 0, y: 4)
        } else {
            configuration.label
                .background(.ultraThinMaterial, in: Circle())
                .overlay(
                    Circle()
                        .strokeBorder(Color.primary.opacity(isDark ? 0.14 : 0.08), lineWidth: 0.5)
                )
                .shadow(color: .black.opacity(isDark ? 0.34 : 0.10), radius: 10, x: 0, y: 4)
                .scaleEffect(configuration.isPressed ? 0.94 : 1.0)
                .animation(.spring(response: 0.24, dampingFraction: 0.78), value: configuration.isPressed)
        }
    }
}

private struct SidebarProminentGlassCapsuleStyle: ButtonStyle {
    let isDark: Bool

    func makeBody(configuration: Configuration) -> some View {
        if #available(iOS 26.0, *) {
            configuration.label
                .glassEffect(
                    .regular.tint(NativeSidebarMetrics.aetherPurple).interactive(),
                    in: .capsule
                )
                .shadow(
                    color: NativeSidebarMetrics.aetherPurple.opacity(isDark ? 0.42 : 0.30),
                    radius: 14,
                    x: 0,
                    y: 6
                )
        } else {
            configuration.label
                .background(NativeSidebarMetrics.aetherPurpleFallback, in: Capsule())
                .shadow(
                    color: NativeSidebarMetrics.aetherPurpleFallback.opacity(0.35),
                    radius: 14,
                    x: 0,
                    y: 6
                )
                .scaleEffect(configuration.isPressed ? 0.95 : 1.0)
                .animation(.spring(response: 0.24, dampingFraction: 0.78), value: configuration.isPressed)
        }
    }
}
