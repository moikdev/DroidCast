# Direct settings command validation

Enhancement [#2](https://github.com/moikdev/DroidCast/issues/2); source baseline `564716c`. This is the first screenshot-backed parity-loop slice.

## Results

- Build and Android lint: pass.
- JVM tests: **21 passed** (18 existing command-engine tests, 3 settings-catalog tests).
- Tablet device tests: **29 passed** (13 settings tests and 16 existing palette/overlay tests).
- Phone-layout checks: **2 passed** (Wi-Fi destination and unavailable-handler query/error preservation).
- Device: dedicated Android 16/API 36 arm64 emulator; tablet 1920×1200, density 240; phone configuration 1080×2340, density 420. Restored to tablet afterward; app/adbd unprivileged.
- APK SHA-256: `99e22f4bf28a749983208015c4c19170c30014f21eeb2e3e1df198c367d1608b`.

## Exercised behavior

All **11** included destinations were launched through palette search/clicks. A non-blocking instrumentation monitor observed the actual intent action, the palette Activity finished, Settings became foreground, and destination-specific content was required before capture (including DroidCast identity, App info, Force stop and Storage & cache for app details). A warm-task regression first opens Accessibility, then requires the real DroidCast app-info page. App-info package URI and notification package extras are covered separately. Capture every destination using the test helper.

The fault-injected missing-handler case leaves the Activity/query intact and shows the error. This exposed and fixed an existing feedback bug: clearing the message before `showSnackbar` completed cancelled its own effect. Messages now remain visible until the host finishes displaying them.

Root Settings remains available, new settings commands introduce no default aliases that could take existing user aliases, and the existing regression tests cover notes, quicklinks, files, sharing, aliases, pins, keyboard actions and overlay dismissal.

## Screenshots

Real framebuffer captures, not mockups. The before search is from baseline `564716c`; after/destination/error captures are from the changed application. The unavailable screenshot is an explicitly injected nonexistent Android action, not a shipped catalog entry.

- [Before Wi-Fi search](../screenshots/parity-system-settings/before-wifi-search.png)
- [After Wi-Fi search](../screenshots/parity-system-settings/after-wifi-search.png)
- [Wi-Fi destination](../screenshots/parity-system-settings/wifi.png)
- [Bluetooth destination](../screenshots/parity-system-settings/bluetooth.png)
- [DroidCast app-info destination](../screenshots/parity-system-settings/app-info.png)
- [Display destination](../screenshots/parity-system-settings/display.png)
- [Unavailable-handler feedback](../screenshots/parity-system-settings/unavailable.png)
- [Phone search](../screenshots/parity-system-settings/phone-wifi-search.png)
- [Phone unavailable-handler feedback](../screenshots/parity-system-settings/phone-unavailable.png)

Other destination captures are alongside these in the same directory.

## Independent audit remediation

Both initial gpt-6.1-sol/high audits flagged that the original generic Settings check could accept an unrelated page; the original app-info capture showed Accessibility. Investigation reproduced a warm-task routing bug: NEW_TASK could foreground the previous Settings page for app-info. Dedicated settings intents now include CLEAR_TOP. Every destination has specific UI assertions, app-scoped identity/arguments are checked, all destination captures were replaced, and the 29 tablet tests were rerun successfully. Both phone checks passed again on the same updated application. The initial evidence is superseded by this validation.

## Boundaries

Battery Saver and Security routes crashed inside this emulator's Android Settings due to missing provider dependencies. They are excluded from this verified slice and remain open in [#22](https://github.com/moikdev/DroidCast/issues/22). This does not implement hardware toggles or all Tinycast system actions. No physical-device/OEM certification is claimed.

An initial harness expected the requested action in `dumpsys activity top`, but Android 16's shared Settings activity does not reliably expose that information there. Final tests observe the real launch intent through an ActivityMonitor and verify actual Settings UI. Earlier failed harness runs are not counted as passing tests.

GitHub CI remains inactive because the available OAuth credential lacks workflow scope. These are **local** checks; `ci/android.yml` remains a template.

## Commands

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
adb shell am instrument -w -r -e class dev.tinycast.android.SystemSettingsDeviceTest,dev.tinycast.android.PaletteDeviceTest,dev.tinycast.android.PaletteOverlayTest dev.tinycast.android.test/androidx.test.runner.AndroidJUnitRunner
```
