# Double-modifier shortcuts

This slice of [#26](https://github.com/moikdev/droidcast/issues/26) adds double Ctrl/Alt/Shift/Meta identities, sided choices, inline recording, conflicts, persistence and backup compatibility. It does not complete Tinycast hotkey parity: single taps, Fn/Globe and Hyper remapping remain open.

## Upstream comparison

Tinycast’s pinned [hotkeys design](https://github.com/abue-ammar/tinycast/blob/37e6043f3f550febb64bdb3cff3ac29a30caad5a/docs/features/hotkeys.md) uses one persisted binding model across chord and modifier engines, sided conflicts, pure timestamped detectors, second-release dispatch and paused recording. This slice adopts those invariants on Android. Tinycast uses a 300ms inter-tap window; this Android implementation uses 350ms, with the same 250ms maximum hold. Tinycast also cancels on mouse clicks; the Android global key filter does not observe pointer-only activity inside an unchanged window. That cancellation gap remains open alongside single modifiers, Fn/Globe, hold-to-talk and Hyper. No native source/runtime assets are copied or changed by this slice.

## Behavior

In Settings → General, choose Change beside the global shortcut; or select any command in Settings → Shortcuts and choose Record. Quickly press and release the same modifier twice, choose Either, Left or Right, then Save shortcut. Either accepts two taps of either physical key; mixing left and right within one sequence does not activate it. Either conflicts with a side-specific binding; distinct Left and Right bindings may target different commands. Conflict feedback names the current owner and disables Save while keeping the side controls available. Clear and confirmed Reset retain their existing behavior.

Two complete DOWN/UP pairs must come from the same device and physical key. Each hold is at most 250ms; the second press starts within 350ms of the first release. Repeats, other keys/modifiers, canceled/stale/missing releases, connection, binding, recording, focus and input-device changes invalidate the pending sequence. Entering and leaving recording changes an epoch even when no key is delivered during recording. SoftInputWindow notifications are excluded from app-window cancellation; only event type/class are inspected, without window text or history.

Global use still requires the optional, user-approved keyboard Accessibility service. Original modifier events are not consumed; ordinary chords keep their existing paired filtering. The connected service owns recognition; MainActivity recognizes taps locally only when disconnected. Both use authenticated, expiring command dispatch. No new permission, text collection, HID remapping or consent grant is introduced. The assistant touch route is independent.

Chord-only configurations continue using shortcut payload version1. Configurations containing taps use version2; new builds read both and reject malformed fields/identities/overlaps. Older builds reject version2. Portable backups retain tap identities and validate against preserved local document bindings; they never transfer document permissions or enable the service.

## Local evidence

The final manifest, APK hashes, source hashes, records and unedited screenshots are in [modifier-taps.json](modifier-taps.json). All test execution is local; CI is inactive. The owned API36 arm64 emulator is Droidcast_PR48_Tablet_API_36_27caf3, serial emulator-5590. Tablet is physical 1920×1200/240dpi; phone is a 1080×2340/420dpi override on that same emulator.

Final checks: successful build, **110 JVM tests**, **39/39 tablet + 39/39 phone**, lint **0 errors/42 warnings**, and a complete hardware rerun with **10/10 core cases + eight capability probes** (six invoked, two Meta probes did not). A focused Clear/Reset correction passed separately and is not added to full-suite totals. **39 real PNGs** include two baseline captures and the retained unexpected cold-failure image. Their raw bytes, dimensions and CRCs match the manifest. App APK SHA-256 is `9549bd312194cc774c637fdb684219651940278d5e08a5f112215d8ade3a1376`; test APK is `dddec0f1cdcad9d170755b83c1fd87b4144d0078cf676e934cfed555c68865bc`. Native coverage includes actual double-Ctrl recording, Either/Right selection, named overlap and unused-side recovery, Tab/Shift+Tab/Enter saving, persisted reopening, clear/reset, versioned backup import, paused recording, long holds, ordinary Ctrl+A/C and local command execution without the keyboard service. Existing shortcut, Settings and readiness suites cover their prior behavior; they do not independently certify every modifier family or double-tap diagnostic check.

Hardware tests run outside UiAutomation while the keyboard service is active. The guarded helper roots adbd only on the explicitly owned emulator for Linux input injection; the application remains an ordinary UID. Binary input_event packets use one stream to avoid per-event ADB fork latency. Every case uses real paired modifier events. Native screenshots use UiAutomation afterward, which can temporarily suppress/rebind other Accessibility services, so the helper checks bound/input-filter readiness before the next sequence. The initial Home screenshot uses screencap and focus only, leaving the immediate warm-toggle check free of UiAutomation. It captures a transitional first frame with the panel lower on screen; it is launch evidence, not settled geometry or motion evidence. Later Home/Settings captures show the settled panel.

Final hardware and native runs use the same app/test APK pair. Ten core cases verify Home/Android Settings activation, warm toggle without duplicate foreground dispatch, Either/Right-only matching, actual Ctrl+A/C/V copy/paste, long-hold rejection/recovery, intervening-key rejection/recovery a Right-Ctrl new-note command, and actual connected-service pause during inline recording followed by Escape and resumed global activation. Eight separate modifier probes run from Android Settings. Both Ctrl, both Alt and both Shift keys activate on this emulator. Neither Meta key activates; those captures show Android Settings, not a working palette. These are context/device observations, not universal support claims. An earlier Double-Shift command attempt from Home did not activate; its cause is unestablished. Fn/Globe were not exercised, and no physical keyboard/OEM certification is claimed.

## Corrections and excluded attempts

Initial native fixture failures concerned a parent/child semantics assertion, native cache synchronization, clipboard access before focus and phone scrolling. They are retained separately and excluded from final passing totals. Historical seven-case runs precede the final epoch correction and new clear/reset case. The first 39-case tablet run passed 38; the new Clear test incorrectly looked for the inspector button while the inline recorder was open. Its selector now uses the recorder’s Clear button. That failed run is excluded. The first focused correction passed Clear but attempted to scroll the fixed Reset footer, which has no scrollable parent. The final fixture clicks that already-visible footer directly.

Hardware setup initially failed service rebinding after force-stop. Explicit consent off/on is test-only setup, not production behavior. Subsequent warm-toggle attempts failed; diagnostic modifier-only logs observed a SoftInputWindow notification between the two taps. The old window-cancellation placement fails the new deterministic unit regression; the corrected source passes it. Faster transport also passed the older warm-toggle path in one run, so the earlier failures do not isolate a single cause or establish an OEM timing fix. Diagnostic logging was removed before the final build.

The first fast-transport copy/paste fixture incorrectly expected Right-arrow navigation to move the search caret. It is replaced by an asserted replacement query followed by Ctrl+A/V restoring the distinctive copied text. A Linux modifier-repeat injection did not establish an Android repeat event and is excluded from hardware claims; repeat cancellation remains deterministic model coverage. A later historical run passed eight core cases before the unsupported Home/Shift command attempt; it is not counted as the final complete run.

An unexpected final-hardware-v2 run failed its first cold Home activation with zero passing cases, on the same app/test APK pair used by the final native runs. Its cleanup assertions passed. The unchanged v3 rerun is recorded separately; success on a rerun establishes neither the cause nor a fix. Cold-activation reliability remains an unresolved validation limitation under #26, rather than a corrected harness finding.

Private palette preferences and clipboard are restored. Clipboard preservation uses a test-only focused activity and a private on-device parcel, never host/public clipboard content. Helpers release injected keys, restore exact preference/secure-setting bytes and viewport geometry, stop the app and restore unrooted adbd. Saved restoration assertions do not establish broader lifecycle or process-death safety.

Stills establish pictured states, not latency or continuous motion. Lenovo/ZUXOS, physical devices, alternate layouts, TalkBack, large fonts and endurance remain uncertified. Upstream runtime assets and notices remain unchanged. Two independent exact-head gpt-6.1-sol/high audits are required before merge.

## Reproduction

Use JDK21 and Android SDK from ANDROID_HOME or local.properties:

```sh
./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
python3 scripts/test-modifier-taps.py \
  --serial emulator-5590 --avd Droidcast_PR48_Tablet_API_36_27caf3 \
  --layout tablet --output artifacts/modifier-tablet
```

The default helper runs the eight new native cases. Use --layout phone with a fresh output directory after restoration. Final regression runs explicitly include ModifierTapDeviceTest, ModifierTapPaletteDeviceTest, ShortcutDeviceTest, SettingsDeviceTest and ShortcutReadinessDeviceTest with --expected 39. Run scripts/test-modifier-taps-hardware.py separately with the same explicit serial/AVD, a fresh output directory and --allow-adbd-root; that flag is restricted to the guarded emulator and is unnecessary for native tests or application use.
