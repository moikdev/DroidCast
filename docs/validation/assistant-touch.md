# Touch invocation through Android's assistant

Issue [#47](https://github.com/moikdev/droidcast/issues/47) adds explicit inline setup for corner swipes and holding Home. The existing default-assistant entry is reused; its session launch and cleanup code are unchanged. This is an Android replacement for launcher activation, not a claim of complete Tinycast/Raycast parity.

## User behavior and boundaries

Choose **Droidcast Settings → General → Android assistant → Choose Droidcast**, then select Droidcast in Android's default digital assistant settings. This replaces the current assistant. Use the new **Navigation settings** link to configure the system gesture:

- Gesture navigation: swipe diagonally inward from either bottom corner; enable the assistant gesture if the device offers it.
- Button navigation: hold the on-screen Home button; enable the assistant action if offered.

The app attempts the package-scoped AOSP navigation page, then standard Android Settings. It catches unavailable/restricted activities and provides manual directions. Android owns the mapping. OEMs can assign Home to Circle to Search or another action; available options and labels vary. No production code writes assistant roles, navigation modes or gesture settings.

The keyboard Accessibility service is independent and is not needed for either gesture. No microphone or overlay permission is requested. The existing system-bound assistant services retain `BIND_VOICE_INTERACTION`, disable assist/screenshot context, and launch only a fixed explicit palette intent. See [Opening Droidcast](../ACTIVATION.md), including primary Android and Launchpad references.

## Evidence scope

[assistant-touch.json](assistant-touch.json) identifies the baseline/final binaries, source files, native records, touch metadata, XML and unedited PNGs. Durable screenshots live under `docs/screenshots/parity-assistant-touch/`. Before captures use nightly r5, source `7f8dea5`, whose tree equals base merge `48e33d0`. The old assistant card lacks the two gesture instructions and navigation link.

The dedicated, independently owned API36 arm64 emulator is `Droidcast_PR48_Tablet_API_36_27caf3`, serial `emulator-5590`. Tablet is physical 1920×1200/240dpi; phone uses a 1080×2340/420dpi override. These are two layouts on one emulator, not two physical devices. Both app and adbd run unprivileged.

Final local checks: successful build, **89 JVM tests**, **26/26 tablet + 26/26 phone cases**, and lint **0 errors/42 warnings**. All 28 published PNGs match their raw captures and recorded hashes/dimensions. The app APK is `a4870145a7f21ec52f28adc02a76aabadac73da16b423bbdddbc80f456f69025`; the final test APK is `4c9775be18ed72c5e5be37b68eafd3de14c67db042b9f0c653d023ccaf3190c4`. Both final layouts use these same binaries, with installed application bytes verified.

Six new native cases per layout cover inline instructions and absence of requested microphone/overlay permissions; the actual navigation destination; controlled unavailable-page fallback into real root Settings; controlled rejection and readable manual recovery; four actual corner swipes; and two actual Home-button holds. Twenty existing Settings, assistant-selection/key and readiness cases also run. Assistant selection uses Android's real user-owned picker. Test-only shell commands select navigation modes and restore them; production only links to Settings.

Corner cases inject diagonal touchscreen swipes from both left and right corners, from both Home and Android Settings. Home cases locate the real SystemUI/Launcher Home control, record its resource/bounds, and inject touchscreen DOWN, a real-time hold of at least 1200ms, then UP. After each gesture, checks require a native palette EditText, focused MainActivity, no enabled keyboard service and a disconnected keyboard runtime. Gesture metadata and post-invocation PNG/XML are retained. Keycode ASSIST and Meta+A run as separate existing regressions and are not offered as touch proof.

**Native hierarchy limitation:** seven final post-gesture XML dumps omit the pictured palette: tablet `home-hold-settings`, `corner-left-settings`, `corner-right-settings`; phone `home-hold-home`, `corner-left-home`, `corner-right-home`, `corner-left-settings`. Some dumps are empty; others expose underlying system windows. Their corresponding unedited PNGs visibly show the palette. The live native EditText and focused-window assertions occur before the generic capture clears the Accessibility cache and saves the hierarchy. These seven XMLs therefore do not independently establish the pictured app state. Their hashes and explicit `palette_edit_text_present: false` are retained in the manifest; no cause, fix or TalkBack equivalence is claimed.

Home taskbar controls are available through interactive-window Accessibility lookup but may be omitted from a saved hierarchy. Each before-hold record explicitly reports `home_exposed_in_xml`; a false value does not certify that XML exposes the control. The actual queried resource/bounds, before PNG, injected touch coordinates, and verified post-invocation palette support the gesture case instead. Instrumentation's UiAutomation can temporarily set Android's Accessibility master flag; that is distinct from granting Droidcast's keyboard service. The host finalizer restores the exact original master flag and enabled-service value.

## Fixture corrections and excluded attempts

The initial six-case tablet run passed 4/6. One failure required Home's taskbar control in an XML dump despite live interactive-window lookup finding it; the other required the Navigation mode title as text although the destination exposed its actual navigation options. The corrected cases record the Home XML limitation and assert both Gesture navigation/3-button navigation plus NavigationModeSettingsActivity. The focused two-case rerun passed. These were fixture corrections, with no product change.

Exploratory attempts using a blocking Home long-click did not reliably leave the palette active under the Compose test clock. The final held-touch fixture advances Compose frames at real-time cadence while holding the actual touchscreen event. The original production assistant launch method passes this fixture. Temporary session/task-launch experiments were reverted; no production lifecycle fix or independently established cause for every exploratory failure is claimed. Initial/exploratory runs are retained locally and excluded from final passing totals.

The first combined phone run passed 23/26: all six new touch/setup cases and all readiness cases passed, while three existing Settings fixtures assumed the global shortcut controls were already on screen. The taller assistant card moves them below the phone viewport. Those fixtures now explicitly scroll to the global shortcut control and assert visibility before capture/click. The earlier tablet 26/26 used the old test APK and remains historical; final totals require the corrected test APK on both layouts. No product change was made for these fixture failures. All eight corrected Settings cases passed in the focused phone rerun before the final combined suites.

The guarded host helper verifies the exact emulator/AVD, shell UID2000, no competing instrumentation and no preexisting viewport override. It verifies installed APK bytes, restores assistant roles and their settings, active navigation overlay, gesture flags, keyboard consent/IME preference, exact palette preference bytes and viewport geometry. Each touch test also restores its own role/navigation state. Private preference backups remain ignored and are not published.

Saved results do not certify Lenovo/ZUXOS delivery, physical devices, other Android versions, TalkBack, large fonts, endurance or gesture latency. Stills alone do not prove continuous motion or speed. CI remains inactive; `local/android` describes local checks only. Packaged upstream runtime assets and notices remain unchanged. Two independent exact-head `gpt-6.1-sol/high` audits are required before merge.

## Reproduction

Using JDK21 and SDK from `ANDROID_HOME` or `local.properties`:

```sh
./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
python3 scripts/test-assistant-touch.py \
  --serial emulator-5590 --avd Droidcast_PR48_Tablet_API_36_27caf3 \
  --layout tablet --output artifacts/assistant-touch-tablet
```

Use `--layout phone` and a fresh output directory for the second layout, after the first run's restoration completes. The default suite expects 26 cases. `--classes` and `--expected` support explicit focused reruns; they do not count as the default suite.
