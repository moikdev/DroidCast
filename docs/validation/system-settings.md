# Direct settings command validation

Enhancement [#2](https://github.com/moikdev/DroidCast/issues/2); source baseline `564716c`. This is the first screenshot-backed parity-loop slice.

## Results

- Build and Android lint: pass.
- JVM tests: **21 passed** (18 existing command-engine tests, 3 settings-catalog tests).
- Tablet device tests: **29 passed** (13 settings tests and 16 existing palette/overlay tests).
- Phone-layout checks: **2 passed** (Wi-Fi destination and unavailable-handler query/error preservation).
- Device: dedicated Android 16/API 36 arm64 emulator; tablet 1920×1200, density 240; phone configuration 1080×2340, density 420. Restored to tablet afterward; app/adbd unprivileged.
- APK SHA-256: `07a298419da88838a8d5645a1ee3cd565534fcafc2af33e9042f6ebba5f8357d`.

## Exercised behavior

All **11** included destinations were launched through palette search/clicks. A non-blocking instrumentation monitor observed the actual intent action, the palette Activity finished, Settings became foreground, and the dedicated page was checked against root Settings. App-info package URI and notification package extras are covered separately. Capture every destination using the test helper.

The fault-injected missing-handler case leaves the Activity/query intact and shows the error. This exposed and fixed an existing feedback bug: clearing the message before `showSnackbar` completed cancelled its own effect. Messages now remain visible until the host finishes displaying them.

Root Settings remains available, new settings commands introduce no default aliases that could take existing user aliases, and the existing regression tests cover notes, quicklinks, files, sharing, aliases, pins, keyboard actions and overlay dismissal.

## Screenshots

Real framebuffer captures, not mockups. The before search is from baseline `564716c`; after/destination/error captures are from the changed application. The unavailable screenshot is an explicitly injected nonexistent Android action, not a shipped catalog entry.

- [Before Wi-Fi search](../screenshots/parity-system-settings/before-wifi-search.png)
- [After Wi-Fi search](../screenshots/parity-system-settings/after-wifi-search.png)
- [Wi-Fi destination](../screenshots/parity-system-settings/wifi.png)
- [Bluetooth destination](../screenshots/parity-system-settings/bluetooth.png)
- [Display destination](../screenshots/parity-system-settings/display.png)
- [Unavailable-handler feedback](../screenshots/parity-system-settings/unavailable.png)
- [Phone search](../screenshots/parity-system-settings/phone-wifi-search.png)
- [Phone unavailable-handler feedback](../screenshots/parity-system-settings/phone-unavailable.png)

Other destination captures are alongside these in the same directory.

## Boundaries

Battery Saver and Security routes crashed inside this emulator's Android Settings due to missing provider dependencies. They are excluded from this verified slice and remain open in [#22](https://github.com/moikdev/DroidCast/issues/22). This does not implement hardware toggles or all Tinycast system actions. No physical-device/OEM certification is claimed.

An initial harness expected the requested action in `dumpsys activity top`, but Android 16's shared Settings activity does not reliably expose that information there. Final tests observe the real launch intent through an ActivityMonitor and verify actual Settings UI. Earlier failed harness runs are not counted as passing tests.

GitHub CI remains inactive because the available OAuth credential lacks workflow scope. These are **local** checks; `ci/android.yml` remains a template.

## Commands

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
adb shell am instrument -w -r -e class dev.tinycast.android.SystemSettingsDeviceTest,dev.tinycast.android.PaletteDeviceTest,dev.tinycast.android.PaletteOverlayTest dev.tinycast.android.test/androidx.test.runner.AndroidJUnitRunner
```
