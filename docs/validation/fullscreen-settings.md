# Fullscreen tablet Settings

Implements #42 after PR #38. Baseline source: `62100080bf247f979d08a236e9e67d7503300154` (same tree as the audited `1508ac6` nightly).

Settings now fills the entire safe drawing viewport. The tablet outer inset, 1160 dp width cap, rounded frame, border and shadow are removed. Sidebar navigation, command table, inline inspector, scrolling forms and compact-height handling remain intact. Form text retains its readable width limit inside the full-width workspace. System bars and keyboard insets remain respected; this does not enable immersive mode or change Android's animation settings.

The command palette remains a separate compact translucent window. Its production layout and activation code are unchanged.

## Validation

See [fullscreen-settings.json](fullscreen-settings.json) for exact APK/test-APK hashes and image checksums.

- Debug/app-test build, 66 JVM tests and lint with 0 errors/39 warnings.
- Sixteen targeted native tests on each layout: SettingsDeviceTest, SettingsRouteDeviceTest, AssistantDeviceTest, plus the compact/translucent palette and immediate-search cases. Geometry assertions require the Settings frame to equal its safe viewport and use the full display width. Navigation, filters, recording, conflicts, aliases/IME, backup review/cancellation, assistant selection/cancellation and restoration remain covered.
- Manual landscape → portrait → landscape check preserves the selected global-shortcut inspector and stored chord, then restores the original free rotation mode.
- Phone overrides are reset, assistant/keyboard-service consent is restored, installed/local APK identity matches and adbd remains unprivileged.

The baseline PNG is copied byte-for-byte from the committed PR #38 tablet General capture. All seven after captures use the new APK: landscape General/Shortcuts, portrait Shortcuts, phone General/Shortcuts and the floating palette on both layouts. These are real, unedited Android 16/API 36 arm64 emulator captures. PR image URLs pin the reviewed commit.

CI remains inactive; `local/android` represents local validation. Physical/OEM, large-font/TalkBack, endurance and broader provider checks are not newly certified. Previous full-suite/hardware results retain their original APK scope; this layout update does not claim they were rerun. Existing #35/#37/#41 limitations remain open.
