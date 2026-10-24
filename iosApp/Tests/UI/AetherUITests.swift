import XCTest

final class AetherUITests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testSettingsProviderRowsAndPersonalizationSaveRespond() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        if app.buttons["Agree"].waitForExistence(timeout: 5) { app.buttons["Agree"].tap() }
        waitForChatStartup(app)
        if app.buttons["Menu"].exists { app.buttons["Menu"].tap() }
        XCTAssertTrue(app.buttons["Settings"].waitForExistence(timeout: 10))
        app.buttons["Settings"].tap()

        let personalization = app.staticTexts["Personalization"].firstMatch
        XCTAssertTrue(personalization.waitForExistence(timeout: 10))
        personalization.tap()
        XCTAssertTrue(app.textViews.firstMatch.waitForExistence(timeout: 10))
        // Save the existing value so the regression test preserves the user's settings.
        let original = app.textViews.firstMatch.value as? String
        app.buttons["Save"].tap()
        let returnedToSettings = app.staticTexts["General Settings"].waitForExistence(timeout: 5)
        XCTAssertTrue(returnedToSettings || app.buttons["Saved"].exists)
        if returnedToSettings {
            personalization.tap()
            XCTAssertTrue(app.textViews.firstMatch.waitForExistence(timeout: 10))
            XCTAssertEqual(app.textViews.firstMatch.value as? String, original)
            app.navigationBars.buttons.element(boundBy: 0).tap()
        }

        let providers = app.staticTexts["Model Providers"].firstMatch
        XCTAssertTrue(providers.waitForExistence(timeout: 10))
        providers.tap()
        XCTAssertTrue(app.buttons["Add provider"].waitForExistence(timeout: 10))
        app.buttons["Add provider"].tap()
        let apiKey = app.buttons["provider-wizard-choice:API key"]
        XCTAssertTrue(apiKey.waitForExistence(timeout: 10))
        // Tap the trailing blank area, far from the leading icon.
        apiKey.coordinate(withNormalizedOffset: CGVector(dx: 0.85, dy: 0.5)).tap()
        let search = app.textFields["Search providers"]
        XCTAssertTrue(search.waitForExistence(timeout: 10))
        search.tap()
        search.typeText("OpenAI")
        let openAI = app.buttons["provider-wizard-choice:OpenAI"]
        XCTAssertTrue(openAI.waitForExistence(timeout: 10))
        openAI.coordinate(withNormalizedOffset: CGVector(dx: 0.65, dy: 0.5)).tap()
        XCTAssertTrue(app.secureTextFields["API key"].waitForExistence(timeout: 10))
        app.buttons["Cancel"].tap()
    }

    func testOrdinaryDeviceKeepsOriginalChatUI() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        if app.buttons["Agree"].waitForExistence(timeout: 5) { app.buttons["Agree"].tap() }
        waitForChatStartup(app)
        XCTAssertTrue(app.buttons["New Chat"].firstMatch.waitForExistence(timeout: 15))
        XCTAssertFalse(app.buttons["Show Agent’s computer"].exists)
        XCTAssertFalse(app.staticTexts["Agent’s computer"].exists)
        XCTAssertFalse(app.buttons["Scheduled Tasks"].exists)
        XCTAssertFalse(app.buttons["Terminal"].exists)
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.lifetime = .keepAlways
        add(attachment)
    }

    func testMessageSubmissionDoesNotTerminateApp() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()

        let privacyAgreement = app.buttons["Agree"]
        if privacyAgreement.waitForExistence(timeout: 5) {
            privacyAgreement.tap()
        }

        waitForChatStartup(app)
        let composer = app.textViews.firstMatch
        XCTAssertTrue(composer.waitForExistence(timeout: 30))
        composer.tap()
        composer.typeText("iOS message submission regression test")

        let send = app.buttons["Send"]
        XCTAssertTrue(send.waitForExistence(timeout: 10))
        send.tap()
        XCTAssertTrue(app.keyboards.firstMatch.waitForNonExistence(timeout: 10))

        for second in 1...30 {
            XCTAssertEqual(
                app.state,
                .runningForeground,
                "Aether left the foreground \(second) seconds after sending a message"
            )
            Thread.sleep(forTimeInterval: 1)
        }
        XCTAssertTrue(composer.waitForExistence(timeout: 10))
    }

    func testBackgroundTapDismissesKeyboardAndPreservesDraft() throws {
        let (app, composer, background) = try openEmptyChatWithKeyboard()
        background.tap()
        XCTAssertTrue(app.keyboards.firstMatch.waitForNonExistence(timeout: 10))
        XCTAssertEqual(composer.value as? String, "Keyboard dismissal draft", composer.debugDescription)

        composer.tap()
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 10))
    }

    func testBackgroundLongPressAndDragKeepKeyboardVisible() throws {
        let (app, composer, background) = try openEmptyChatWithKeyboard()
        let keyboard = app.keyboards.firstMatch

        composer.tap()
        XCTAssertTrue(keyboard.exists, "Tapping the input must keep the keyboard open")

        background.press(forDuration: 0.8)
        XCTAssertTrue(keyboard.exists, "A long press must not dismiss the keyboard")

        // An empty conversation cannot scroll, so this also checks movement rejection
        // when no scrolling child consumes the drag.
        background.press(
            forDuration: 0.05,
            thenDragTo: background.withOffset(CGVector(dx: 0, dy: -100)),
            withVelocity: .fast,
            thenHoldForDuration: 0
        )
        XCTAssertTrue(keyboard.exists, "A vertical drag must not dismiss the keyboard")
        // Drag left to avoid opening the navigation drawer before the next tap.
        background.press(
            forDuration: 0.05,
            thenDragTo: background.withOffset(CGVector(dx: -80, dy: 0)),
            withVelocity: .fast,
            thenHoldForDuration: 0
        )
        XCTAssertTrue(keyboard.exists, "A horizontal drag must not dismiss the keyboard")
        XCTAssertEqual(composer.value as? String, "Keyboard dismissal draft")

        background.press(
            forDuration: 0.05,
            thenDragTo: background.withOffset(CGVector(dx: 2, dy: 1)),
            withVelocity: .fast,
            thenHoldForDuration: 0
        )
        XCTAssertTrue(
            keyboard.waitForNonExistence(timeout: 10),
            "A short tap with minor finger movement should dismiss the keyboard"
        )
    }

    func testComposerStaysAtBottomAfterOrientationRoundTrip() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()

        let privacyAgreement = app.buttons["Agree"]
        if privacyAgreement.waitForExistence(timeout: 5) {
            privacyAgreement.tap()
        }

        let composer = app.textViews.firstMatch
        XCTAssertTrue(composer.waitForExistence(timeout: 30))
        composer.tap()
        composer.typeText("orientation-regression")

        XCUIDevice.shared.orientation = .landscapeLeft
        XCTAssertTrue(waitForLandscape(app))
        XCUIDevice.shared.orientation = .portrait
        XCTAssertTrue(waitForPortrait(app))
        XCTAssertTrue(composer.waitForExistence(timeout: 10))
        XCTAssertLessThan(app.frame.maxY - composer.frame.maxY, 120)
    }

    func testKeyboardKeepsComposerVisibleAndSidebarStationary() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        let privacyAgreement = app.buttons["Agree"]
        if privacyAgreement.waitForExistence(timeout: 5) {
            privacyAgreement.tap()
        }
        waitForChatStartup(app)
        let composer = app.textViews.firstMatch
        XCTAssertTrue(composer.waitForExistence(timeout: 30))
        let settings = app.buttons["Settings"]
        let sidebarFrame = settings.exists ? settings.frame : nil
        composer.tap()
        let keyboard = app.keyboards.firstMatch
        XCTAssertTrue(keyboard.waitForExistence(timeout: 10))
        let settled = NSPredicate { _, _ in
            composer.frame.maxY <= keyboard.frame.minY && composer.frame.height > 0
        }
        expectation(for: settled, evaluatedWith: app)
        waitForExpectations(timeout: 10)
        if UIDevice.current.userInterfaceIdiom == .pad {
            let originalFrame = try XCTUnwrap(sidebarFrame)
            XCTAssertEqual(settings.frame.minY, originalFrame.minY, accuracy: 1)
            XCTAssertEqual(settings.frame.minX, originalFrame.minX, accuracy: 1)
        }
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }

    func testOnboardingRuntimeAndIOSCapabilitySurface() throws {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()

        let privacyAgreement = app.buttons["Agree"]
        if privacyAgreement.waitForExistence(timeout: 10) {
            privacyAgreement.tap()
        }
        XCTAssertTrue(app.buttons["Get started"].waitForExistence(timeout: 20))
        XCTAssertTrue(app.staticTexts["Welcome to Aether"].exists)
        app.buttons["Get started"].tap()

        XCTAssertTrue(app.staticTexts["Set up the built-in Alpine Linux environment. It stays inside Aether's private app storage."].waitForExistence(timeout: 20))
        XCTAssertTrue(app.buttons["Initialize"].waitForExistence(timeout: 20))
        app.buttons["Initialize"].tap()
        let setupDetails = app.descendants(matching: .any)
            .matching(identifier: "Details")
            .firstMatch
        XCTAssertTrue(setupDetails.waitForExistence(timeout: 20))
        setupDetails.tap()
        XCTAssertTrue(app.staticTexts["Setup details"].waitForExistence(timeout: 10))
        app.buttons["Close"].tap()
        XCTAssertTrue(app.buttons["Continue"].waitForExistence(timeout: 300))
        XCTAssertTrue(app.staticTexts["Alpine is ready and will be used as the default local runtime."].exists)
        app.buttons["Continue"].tap()

        XCTAssertTrue(app.buttons["Skip"].waitForExistence(timeout: 30))
        app.buttons["Skip"].tap()
        XCTAssertTrue(app.staticTexts["What can I help with?"].waitForExistence(timeout: 30))

        let composer = app.textViews.firstMatch
        XCTAssertTrue(composer.waitForExistence(timeout: 10))
        composer.tap()
        composer.typeText("keyboard-e2e")
        XCTAssertTrue((composer.value as? String)?.contains("keyboard-e2e") == true)

        if UIDevice.current.userInterfaceIdiom == .pad {
            XCTAssertFalse(app.buttons["Menu"].exists)
            XCTAssertTrue(app.buttons["Settings"].waitForExistence(timeout: 10))
        } else {
            XCTAssertTrue(app.buttons["Menu"].exists)
            app.buttons["Menu"].tap()
            XCTAssertTrue(app.buttons["Settings"].waitForExistence(timeout: 10))
        }
        app.buttons["Settings"].tap()

        XCTAssertTrue(app.staticTexts["General Settings"].waitForExistence(timeout: 15))
        XCTAssertTrue(app.staticTexts["Model Providers"].exists)
        XCTAssertTrue(app.staticTexts["Personalization"].exists)
        XCTAssertTrue(app.staticTexts["Reliability"].exists)
        XCTAssertTrue(app.staticTexts["Agent Skills"].exists)
        XCTAssertTrue(app.staticTexts["Extensions"].exists)
        app.swipeUp()
        XCTAssertTrue(app.staticTexts["Alpine"].exists)
        app.swipeUp()
        XCTAssertTrue(app.staticTexts["About"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.staticTexts["Termux"].exists)
        XCTAssertFalse(app.staticTexts["Runtime defaults"].exists)
        XCTAssertFalse(app.staticTexts["Phone Use"].exists)
        XCTAssertFalse(app.staticTexts["Scheduled Tasks"].exists)

        XCUIDevice.shared.orientation = .landscapeLeft
        XCTAssertTrue(app.staticTexts["Settings"].waitForExistence(timeout: 10))
        XCUIDevice.shared.orientation = .portrait
        XCTAssertTrue(app.staticTexts["Settings"].waitForExistence(timeout: 10))

        for _ in 0..<3 where !app.staticTexts["General Settings"].isHittable {
            app.swipeDown()
        }
        XCTAssertTrue(app.staticTexts["General Settings"].isHittable)

        let generalSettings = app.buttons
            .matching(NSPredicate(format: "label BEGINSWITH %@", "General Settings,"))
            .firstMatch
        XCTAssertTrue(generalSettings.waitForExistence(timeout: 10))
        generalSettings.tap()
        XCTAssertTrue(app.staticTexts["Language"].waitForExistence(timeout: 10))
        app.buttons["System, Theme"].tap()
        let darkTheme = app.descendants(matching: .any)
            .matching(identifier: "Dark")
            .firstMatch
        XCTAssertTrue(darkTheme.waitForExistence(timeout: 10))
        darkTheme.tap()
        app.buttons["English, Language"].tap()
        let simplifiedChinese = app.descendants(matching: .any)
            .matching(identifier: "简体中文")
            .firstMatch
        XCTAssertTrue(simplifiedChinese.waitForExistence(timeout: 10))
        simplifiedChinese.tap()

        app.terminate()
        let localizedApp = XCUIApplication()
        localizedApp.launch()

        XCTAssertTrue(localizedApp.staticTexts["想让我帮你做什么？"].waitForExistence(timeout: 30))
        let localizedDarkHome = XCTAttachment(screenshot: localizedApp.screenshot())
        localizedDarkHome.name = "Chinese dark chat home"
        localizedDarkHome.lifetime = .keepAlways
        add(localizedDarkHome)
    }

    private func openEmptyChatWithKeyboard() throws -> (XCUIApplication, XCUIElement, XCUICoordinate) {
        let app = XCUIApplication()
        app.launchArguments += ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        let privacyAgreement = app.buttons["Agree"]
        if privacyAgreement.waitForExistence(timeout: 5) {
            privacyAgreement.tap()
        }
        let newChat = app.buttons["New chat"].firstMatch
        waitForChatStartup(app)
        newChat.tap()
        let composer = app.textViews.firstMatch
        XCTAssertTrue(composer.waitForExistence(timeout: 10))
        composer.tap()
        composer.typeText("Keyboard dismissal draft")
        let keyboard = app.keyboards.firstMatch
        XCTAssertTrue(keyboard.waitForExistence(timeout: 10))
        let settled = XCTNSPredicateExpectation(
            predicate: NSPredicate { _, _ in composer.frame.maxY <= keyboard.frame.minY },
            object: nil
        )
        XCTAssertEqual(XCTWaiter.wait(for: [settled], timeout: 10), .completed)
        XCTAssertEqual(composer.value as? String, "Keyboard dismissal draft", composer.debugDescription)
        XCTAssertTrue(app.buttons["Send"].isEnabled)
        let background = app.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(
            dx: composer.frame.midX - app.frame.minX,
            dy: (composer.frame.minY - app.frame.minY) / 2
        ))
        return (app, composer, background)
    }

    private func waitForChatStartup(_ app: XCUIApplication) {
        XCTAssertTrue(app.buttons["New chat"].firstMatch.waitForExistence(timeout: 30))
        let loading = app.descendants(matching: .any)["aether-startup-loading"]
        XCTAssertTrue(loading.waitForNonExistence(timeout: 120))
    }

    private func waitForLandscape(_ app: XCUIApplication) -> Bool {
        let expectation = XCTNSPredicateExpectation(
            predicate: NSPredicate { _, _ in app.frame.width > app.frame.height },
            object: nil
        )
        return XCTWaiter.wait(for: [expectation], timeout: 10) == .completed
    }

    private func waitForPortrait(_ app: XCUIApplication) -> Bool {
        let expectation = XCTNSPredicateExpectation(
            predicate: NSPredicate { _, _ in app.frame.height > app.frame.width },
            object: nil
        )
        return XCTWaiter.wait(for: [expectation], timeout: 10) == .completed
    }
}
