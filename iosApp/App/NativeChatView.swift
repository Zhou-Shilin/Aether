import SwiftUI
import UIKit
import AetherShared

/// Owns presentation state only; the Kotlin session remains the source of truth.
@MainActor
final class NativeChatFactory: NSObject, IosNativeChatFactory {
    static let shared = NativeChatFactory()
    func create(kind: String, snapshot: String, handler: any IosNativeChatActionHandler) -> UIViewController {
        NativeChatController(kind: kind, snapshot: snapshot, handler: handler)
    }
    func update(controller: UIViewController, snapshot: String) {
        (controller as? NativeChatController)?.model.receive(snapshot)
    }
}

@MainActor
private final class ChatControlModel: ObservableObject {
    @Published var data: [String: Any] = [:]
    let handler: any IosNativeChatActionHandler
    init(snapshot: String, handler: any IosNativeChatActionHandler) {
        self.handler = handler
        receive(snapshot)
    }
    func receive(_ snapshot: String) {
        guard let bytes = snapshot.data(using: .utf8),
              let value = try? JSONSerialization.jsonObject(with: bytes) as? [String: Any],
              !NSDictionary(dictionary: data).isEqual(to: value) else { return }
        data = value
    }
    func text(_ key: String) -> String { data[key] as? String ?? "" }
    func flag(_ key: String) -> Bool { data[key] as? Bool ?? false }
    func rows(_ key: String) -> [[String: Any]] { data[key] as? [[String: Any]] ?? [] }
    func send(_ action: String, _ value: String = "", completion: @escaping (Bool) -> Void = { _ in }) {
        handler.handle(action: action, value: value) { completion($0.boolValue) }
    }
}

@MainActor
private final class NativeChatController: UIHostingController<NativeChatControlView> {
    let model: ChatControlModel
    // Keep system menu focus within the native host instead of forwarding it
    // to Compose's text-input responder, which would summon the keyboard.
    private let menuInputView = UIView(frame: .zero)
    override var canBecomeFirstResponder: Bool { true }
    override var inputView: UIView? { menuInputView }
    init(kind: String, snapshot: String, handler: any IosNativeChatActionHandler) {
        model = ChatControlModel(snapshot: snapshot, handler: handler)
        super.init(rootView: NativeChatControlView(kind: kind, model: model))
        safeAreaRegions = []
        view.backgroundColor = .clear
    }
    @available(*, unavailable) required init?(coder: NSCoder) { fatalError() }
}

private struct NativeChatControlView: View {
    let kind: String
    @ObservedObject var model: ChatControlModel
    private var selected: [String: Any]? {
        model.rows("options").first { $0.string("id") == model.text("selected") }
    }
    var body: some View {
        GeometryReader { geometry in
            // Keep the interaction shape on the Kotlin button. Only the native
            // menu preview anchor moves beyond its edge, leaving an 8pt gap.
            let offset = (geometry.size.height + 8) * (kind == "model" ? 1.0 : -1.0)
            control(anchorOffset: offset)
        }
    }
    private func control(anchorOffset: CGFloat) -> some View {
        Group {
            if kind == "model" {
                Menu {
                    Picker(model.text("modelTitle"), selection: Binding(
                        get: { model.text("selected") },
                        set: { model.send("model", $0) }
                    )) {
                        ForEach(model.rows("options"), id: \.identity) { option in
                            Text(option.string("title")).tag(option.string("id"))
                        }
                    }
                    if let selected, let efforts = selected["efforts"] as? [[String: Any]], !efforts.isEmpty {
                        Menu(model.text("reasoningTitle")) {
                            Picker(model.text("reasoningTitle"), selection: Binding(
                                get: { selected.string("effectiveEffort") },
                                set: { model.send("effort", $0) }
                            )) {
                                ForEach(efforts, id: \.identity) { effort in
                                    Text(effort.string("title")).tag(effort.string("id"))
                                }
                            }
                        }.accessibilityIdentifier("chat.menu.reasoning")
                    }
                } label: {
                    Color.clear
                        .contentShape(Rectangle())
                        .contentShape(.contextMenuPreview, Capsule().offset(y: anchorOffset))
                }
                .menuStyle(.button)
                .buttonStyle(.plain)
                .menuOrder(.fixed)
                .disabled(model.rows("options").isEmpty)
                .accessibilityLabel([model.text("primary"), model.text("secondaryName")].filter { !$0.isEmpty }.joined(separator: " "))
                .accessibilityIdentifier("chat.modelSelector")
            } else if kind == "plus" {
                // Compose continues to draw the original plus. This clear Menu label
                // anchors the system menu to that same button without redrawing it.
                Menu {
                    menuItems
                    if model.flag("customMenu") {
                        // Extensions can provide arbitrary Compose content. Keep that
                        // content reachable from a standard system menu action.
                        Button(model.text("more"), systemImage: "puzzlepiece.extension") { model.send("open") }
                    }
                } label: {
                    Color.clear
                        .contentShape(Circle())
                        .contentShape(.contextMenuPreview, Circle().offset(y: anchorOffset))
                }
                .menuStyle(.button)
                .buttonStyle(.plain)
                .menuOrder(.fixed)
                .accessibilityLabel(model.text("label"))
                .accessibilityIdentifier("chat.add")
            }
        }
        .preferredColorScheme(model.flag("dark") ? .dark : .light)
    }
    @ViewBuilder private var menuItems: some View {
        ForEach(model.rows("items"), id: \.identity) { item in
            if item["selected"] as? Bool == true {
                Toggle(isOn: Binding(get: { true }, set: { _ in model.send("item", item.string("id")) })) {
                    Label(item.string("title"), systemImage: item.string("icon"))
                }.accessibilityIdentifier("chat.menu." + item.string("id"))
            } else {
                Button { model.send("item", item.string("id")) } label: {
                    Label(item.string("title"), systemImage: item.string("icon"))
                }.accessibilityIdentifier("chat.menu." + item.string("id"))
            }
        }
    }
}

private extension Dictionary where Key == String, Value == Any {
    func string(_ key: String) -> String { self[key] as? String ?? "" }
    var identity: String { string("id") }
}
