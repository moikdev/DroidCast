# Keyboard setup and delivery readiness

Implements issue #39 on base `703ddb0849addc2df55e1c8e00ec925eea182a57` (the audited r4 tree). Tinycast's configurable summon shortcut remains the reference workflow; this slice addresses Android's separate service approval and connection requirements without changing upstream runtime assets.

## Behavior

Settings → General keeps setup in the existing scrollable workspace. It distinguishes the saved global chord, Android Accessibility approval and the service's live connection. Full and shortened component names are parsed exactly; similarly named packages/services do not count. The master accessibility switch is checked separately. A late disconnect from an old service cannot clear a newer instance's connection.

The optional inline check is enabled only when a global chord, approval and connection are present. It listens for the current configured palette shortcut for 15 seconds. Only service-filtered target/chord identities reach it, with no typed text, screen-content retrieval or key history. Receipt is explicitly local to Settings; users are told to verify activation from another app afterward. Other commands continue through normal dispatch. Timeout and cancellation do not claim success or change consent. Backgrounding, focus loss, page changes, changed bindings, approval loss and disconnect cancel an active check.

Setup steps include the App info / More / Allow restricted settings path where available, an Accessibility link, manual fallback, Google's instructions and alternatives when keyboard approval is declined. The application never writes Android consent settings. The app icon, Open palette, Quick Settings tile and optional Android assistant remain available. System Settings and browser launch failures use readable feedback.

Primary references: [Android restricted-settings instructions](https://support.google.com/android/answer/12623953?hl=en) and [AccessibilityService lifecycle](https://developer.android.com/reference/android/accessibilityservice/AccessibilityService#onServiceConnected()). Android controls restricted-settings approval; this app cannot silently grant it.

## Validation

Final local checks pass: **78 JVM tests, 34 tablet native cases (7 readiness +27 regressions), 34 phone native cases (7+27), eight hardware cases, build and lint with 0 errors/42 warnings**. One added warning is a nonblocking `UseKtx` suggestion at the App info URI. Exact APK/test-APK hashes, installed-byte equality, screenshot/native XML hashes and restoration records are in [shortcut-readiness.json](shortcut-readiness.json). Checks are scoped to that binary and device; this is not active CI.

New coverage includes seven JVM cases for readiness, connection ownership and probe identities/deadlines/cancellation, plus seven native cases per layout for actual ComponentName parsing, inline setup and alternatives, exact Android destinations, real service enable/disable/short-name reconnect, navigation/background cancellation, timeout and an unassigned shortcut. The App info test requires Droidcast identity, App info, Force stop and Storage & cache; Accessibility requires Downloaded apps, the Droidcast service and TalkBack.

The hardware helper performs eight real Linux qwerty2 cases: record/persist Alt+Q, summon/dismiss from Settings and Home, no global activation without consent, reconnect persistence, inline receipt without leaving Settings, timeout and normal activation after timeout. adbd is rooted only for guarded emulator event injection; the application runs under its ordinary app UID. The helper restores preference bytes and service consent, releases keys and unroots adbd in cleanup.

UiAutomator hierarchy dumps can temporarily disconnect/rebind the service. The hardware check avoids a dump while listening; its verification XML records the retained result, and the corresponding PNG is explicitly captured after rebind. These captures do not establish the connection state at the earlier exact event instant; the recorded filtered receipt, focus assertion and live reconnect checks provide the relevant evidence.

Final native regression scope also covers the existing Settings workspace, inline shortcut recorder, keyboard navigation, aliases, backup cancellation, settings routes, ordinary shortcut dispatch/backup and floating/translucent immediate-search palette. This does not claim an all-feature full-suite run.

## Provenance and exclusions

Final validation uses the independently owned `Droidcast_PR48_Tablet_API_36_27caf3` emulator on serial `emulator-5590`: API36 arm64, tablet1920×1200/240dpi, and phone1080×2340/420dpi override. Native tests require an explicitly matching dedicated AVD; hardware tests additionally reject active instrumentation and require unprivileged starting adbd. Other shared emulators are not used. Consent and physical geometry restoration are recorded separately for each layout.

The before image is a fresh real Settings capture from released r4 APK/source `a101671`, whose tree equals the base merge. Final native and hardware captures use the development APK identified in the manifest. No mockups or edited images are used. Hardware after-rebind images retain that qualification.

The first native tablet attempt passed 5/7: one assertion needed to scroll to the App info button, and the Accessibility heading was exposed as a content description rather than text. A second attempt passed 6/7 while retaining a PNG/XML showing the correct Accessibility destination. The final destination check requires specific page controls and service identity. Those failed fixture runs are retained and excluded from final passing outcomes. The older preserved WIP and its malformed quoted settings helper are not final validation evidence; the corrected helper validates single tokens and reads back every settings write.

## Reproduction

Build with JDK21 and the SDK configured in `local.properties` or `ANDROID_HOME`:

```sh
./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
```

On a disposable dedicated API36 emulator, install both debug APKs, then run `ShortcutReadinessDeviceTest` with `-e dedicatedAvd EXACT_AVD_NAME`. Run `SettingsDeviceTest`, `SettingsRouteDeviceTest`, `ShortcutDeviceTest` and the two palette overlay regressions separately. The native suite saves PNG/XML and restoration records under the app's private `readiness-evidence` directory; collect it with `run-as`.

Run the hardware helper only after instrumentation finishes:

```sh
python3 scripts/test-configurable-shortcuts.py --adb adb --serial SERIAL --avd EXACT_AVD_NAME --readiness --rounds 1
```

The hardware helper is a test facility; normal app use does not require root. Do not publish its ignored private preference snapshot.

## Remaining limits

CI is inactive; `local/android` represents local checks. This is not an independently verified Lenovo/ZUXOS, physical keyboard/OEM, other API/IME, TalkBack/large-font or endurance certification. The user's Lenovo Alt+Q success after approving restricted settings remains user-reported. The emulator does not reproduce that OEM restriction warning, so the help path is grounded in Google's instructions rather than a claim of automated OEM approval testing.

Assistant corner/Home setup #47, double-tap modifiers #26, broad parity and existing #5/#35/#37/#41 gaps remain separate. This slice does not enable keyboard consent or claim permission-free custom hotkeys.
