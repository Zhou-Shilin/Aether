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

## Touch ownership

The shared drawer suppresses swipe recognition only while a pointer sequence starts
inside a registered extension web panel. A passive observer runs in Compose's
Initial pointer pass; it does not consume WebView or vertical scroll events. The
lease lasts until every pointer is released, cancelled, or the region leaves
composition. An already-open drawer retains its closing gesture; the menu button
and touches outside the web panel keep their normal behavior. Android's native
extension WebView and iOS's shared extension renderer mark the same region, and
both mobile drawers use the shared guard. No HTML focus callback or model request
is involved.

This follows [Android's drawer gesture control](https://developer.android.com/develop/ui/compose/components/drawer)
and [Compose event propagation](https://developer.android.com/develop/ui/compose/touch-input/pointer-input/understand-gestures).
[UIKit interop guidance](https://kotlinlang.org/docs/multiplatform/compose-ios-touch.html)
warns that giving every touch to a native view can prevent parent scrolling; this
patch preserves the existing interop policy rather than enabling NonCooperative mode.

Three desktop Robolectric/Compose UI tests cover panel swipe isolation, restoration
after cancellation, and vertical parent scrolling. The Android hosted APK builds.
These tests use a Compose panel to exercise the guard; real WebView gestures and
iOS device interaction remain manual validation, with physical input performed by
the device tester.
