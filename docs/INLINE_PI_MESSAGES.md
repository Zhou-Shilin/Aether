# Inline Pi extension messages

A Pi extension can queue a visible custom message with
`pi.sendMessage({customType, content, details, display:true}, {triggerTurn:false})`.
This patch delivers those persisted entries into their originating native chat,
where an Aether extension's registered message renderer supplies the UI.

The bridge waits for Pi to settle, then includes only newly committed visible
custom messages from the active branch in the completion payload. A shared Kotlin
parser bounds text and JSON details. Android and iOS persist these messages in the
originating session. Android exposes tagged messages to the extension context and
reuses its existing extension view renderer in both chat message layouts; iOS
uses its existing custom-message renderer. Repeated Android startup calls preserve
the previous context so a new figure causes a rendering refresh.

No visual toolkit, generated document, sample-specific code or provider SDK is
bundled in native code. Independently installed extensions own their documents
and interaction. This patch has no dependency on the separate web-app-host proposal.

## Examples

[Interactive UI and Anatomy](https://github.com/josephm1/aether-visuals) are separate
extensions exercising this generic path. These actual Android captures show
model-generated content inline with the conversation. Physical interaction is
performed by the device tester; a screenshot establishes display, not full
correctness or interaction coverage.

<p><img src="https://raw.githubusercontent.com/josephm1/aether-visuals/311803d/docs/screenshots/interactive-triangle-android.png" width="280" alt="Interactive triangle inline in Android chat"><img src="https://raw.githubusercontent.com/josephm1/aether-visuals/311803d/docs/screenshots/anatomy-dial-android.png" width="280" alt="Contained Anatomy dial indicator inline in Android chat"></p>

The device runs a local hosted build that includes this generic patch alongside
other local features. The isolated PR branch is checked separately against upstream
main. iOS source is connected, but Xcode compilation and device validation remain
pending on a Mac. Linux browser tests do not establish iOS parity.
