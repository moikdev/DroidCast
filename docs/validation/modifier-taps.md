# Double-modifier shortcuts

This slice of [#26](https://github.com/moikdev/droidcast/issues/26) adds double Ctrl/Alt/Shift/Meta identities, sided choices, inline recording, conflicts, persistence and backup compatibility. It does not complete Tinycast hotkey parity: single taps, Fn/Globe and Hyper remapping remain open.

## Upstream comparison

Tinycast’s pinned [hotkeys design](https://github.com/abue-ammar/tinycast/blob/37e6043f3f550febb64bdb3cff3ac29a30caad5a/docs/features/hotkeys.md) uses one persisted binding model across chord and modifier engines, sided conflicts, pure timestamped detectors, second-release dispatch and paused recording. This slice adopts those invariants on Android. Tinycast uses a 300ms inter-tap window; this Android implementation uses 350ms, with the same 250ms maximum hold. Tinycast also cancels on mouse clicks; the Android global key filter does not observe pointer-only activity inside an unchanged window. That cancellation gap remains open alongside single modifiers, Fn/Globe, hold-to-talk and Hyper. No native source/runtime assets are copied or changed by this slice.

## Behavior

In Settings → General, choose Change beside the global shortcut; or select any command in Settings → Shortcuts and choose Record. Quickly press and release the same modifier twice, choose Either, Left or Right, then Save shortcut. Either accepts two taps of either physical key; mixing left and right within one sequence does not activate it. Either conflicts with a side-specific binding; distinct Left and Right bindings may target different commands. Conflict feedback names the current owner and disables Save while keeping the side controls available. Clear and confirmed Reset retain their existing behavior.

Two complete DOWN/UP pairs must come from the same device and physical key. Each hold is at most 250ms; the second press starts within 350ms of the first release. Repeats, other keys/modifiers, canceled/stale/missing releases, connection, binding, recording, focus and input-device changes invalidate the pending sequence. Entering and leaving recording changes an epoch even when no key is delivered during recording. SoftInputWindow notifications are excluded from app-window cancellation. Delayed notifications timestamped before the first press cannot cancel a newer sequence; new or unknown-time changes still cancel it. Only event type, class and timestamp are inspected, without window text or history. Settings also discards partial recording when an input device changes or is removed.

Global use still requires the optional, user-approved keyboard Accessibility service. Original modifier events are not consumed; ordinary chords keep their existing paired filtering. The connected service owns recognition; MainActivity recognizes taps locally only when disconnected. Both use authenticated, expiring command dispatch. No new permission, text collection, HID remapping or consent grant is introduced. The assistant touch route is independent.

Chord-only configurations continue using shortcut payload version1. Configurations containing taps use version2; new builds read both and reject malformed fields/identities/overlaps. Older builds reject version2. Portable backups retain tap identities and validate against preserved local document bindings; they never transfer document permissions or enable the service.

## Local evidence

The final manifest, APK hashes, source hashes, records and unedited screenshots are in [modifier-taps.json](modifier-taps.json). All test execution is local; CI is inactive. The owned API36 arm64 emulator is Droidcast_PR48_Tablet_API_36_27caf3, serial emulator-5590. Tablet is physical 1920×1200/240dpi; phone is a 1080×2340/420dpi override on that same emulator.

Final corrected-binary results: **113 JVM**, **41/41 tablet**, **41/41 phone**, **12/12 hardware core cases + eight capability probes**, four host cleanup tests, successful build and lint **0 errors/42 warnings**. Six probes invoked (both Ctrl/Alt/Shift); both Meta probes did not. The core total includes one six-rebind case, not six separate test cases. The **42 PNGs** comprise two baseline, 37 final production, one historical untraced failure and two diagnostic images; 38 have paired XML. App APK SHA-256: `8ff881ce95ec90669105a3859ffeda2bdc675c265c9ea5faf2e26ed625324c0c`; test APK: `d427f00df5240af513d4f5921f18b570e085c94aea3d24cc1a8d157d78f4e308`. Saved final records verify installed equality and all restoration actions.

Final checks are recorded in the manifest, including build/JVM/lint reports, native per-case statuses and hardware input timestamps. Final tablet and phone suites run the ten modifier cases plus the existing shortcut, Settings and readiness regressions. Native cases cover recording, side conflicts, keyboard-only side selection and Save without preparatory scrolling, persisted reopening, Clear/Reset, backup import, input-device invalidation, paused recording, holds, ordinary chords and local dispatch without the keyboard service.

Hardware tests run outside UiAutomation while the keyboard service is active. The test-only C helper creates paired Linux input events with **on-device CLOCK_MONOTONIC timing**, records each write's before/after timestamps and validates the intervals. Host sleeps and a single ADB stream alone do not establish input timing. Root is guarded to this explicitly named QEMU emulator; the application remains an ordinary UID. The helper verifies service/input-filter readiness before each sequence because hierarchy capture can temporarily suppress/rebind other Accessibility services.

Hardware core cases cover Home/Settings activation, warm toggle without duplicate foreground dispatch, Either/Right-only matching, real Ctrl+A/C/V, hold and intervening-key rejection/recovery, Right-Ctrl note dispatch, connected-service pause during recording and resume after Escape, the actual double-Ctrl Settings delivery check, and six separate cold Home launches with service rebinding. Eight additional capability probes cover both physical sides of Ctrl/Alt/Shift/Meta from Android Settings. These are emulator observations, not universal support claims. The immediate first Home PNG is launch/focus evidence rather than settled geometry; later captures show the settled panel.

## Controlled cold-launch investigation

The reviewed dc28533 APK had an untraced first Home miss; an unchanged rerun passed. That original miss remains historical evidence and has no retrospective cause proof. The investigation separately distinguished kernel input delivery, service receipt, cancellation, recognition, dispatch and activity acceptance using a diagnostic build with modifier-only logging. No typed text or token values were logged. **Diagnostic APKs are not release APKs.**

The controlled experiment deliberately used a **500ms Accessibility notification timeout**, compared with the unchanged **100ms production setting**, to expose delayed window delivery. Both negative and corrected tracing builds used the same experimental timeout and device-clock injector. In negative trial02, all four valid Ctrl events reached the service. A Home notification with eventTime **19391288**, sent before first Ctrl DOWN **19391691**, arrived between second DOWN **19391812** and UP **19391856**. The old unconditional window reset cancelled recognition. This is a cancellation failure, not missing Linux input or a failed command dispatch.

The timestamp-aware tracing build passed all four repetitions, including trials00 and02 with the same stale-notification interleaving during the keys. Those cases recognized, dispatched and accepted the invocation. A deterministic unit replay uses the captured timestamps; reverting to unconditional window clearing makes both old-notification regressions fail. Fresh, equal and unknown timestamp changes retain cancellation coverage. This establishes the reproduced race and correction, without proving the exact cause of the original untraced miss or certifying physical/OEM timing.

Diagnostic records, APK identities, source snapshots, kernel/service traces and restoration assertions are indexed under `cold_investigation` in the manifest. Two diagnostic PNGs are explicitly labelled tracing-build evidence. Final native/hardware suites use the same untraced production APK pair and the normal 100ms configuration.

## Corrections and excluded attempts

Initial native fixture failures concerned a parent/child semantics assertion, native cache synchronization, clipboard access before focus and phone scrolling. They are retained separately and excluded from final passing totals. Historical seven-case runs precede the final epoch correction and new clear/reset case. The first 39-case tablet run passed 38; the new Clear test incorrectly looked for the inspector button while the inline recorder was open. Its selector now uses the recorder’s Clear button. That failed run is excluded. The first focused correction passed Clear but attempted to scroll the fixed Reset footer, which has no scrollable parent. The final fixture clicks that already-visible footer directly.

Hardware setup initially failed service rebinding after force-stop. Explicit consent off/on is test-only setup, not production behavior. Subsequent warm-toggle attempts failed; diagnostic modifier-only logs observed a SoftInputWindow notification between the two taps. The old window-cancellation placement fails the new deterministic unit regression; the corrected source passes it. Faster transport also passed the older warm-toggle path in one run, so the earlier failures do not isolate a single cause or establish an OEM timing fix. Diagnostic logging was removed before the final build.

The first fast-transport copy/paste fixture incorrectly expected Right-arrow navigation to move the search caret. It is replaced by an asserted replacement query followed by Ctrl+A/V restoring the distinctive copied text. A Linux modifier-repeat injection did not establish an Android repeat event and is excluded from hardware claims; repeat cancellation remains deterministic model coverage. A later historical run passed eight core cases before the unsupported Home/Shift command attempt; it is not counted as the final complete run.

The historical final-hardware-v2 run failed its first cold Home activation with zero passing cases on the reviewed dc28533 APK. Its unchanged v3 rerun passed; both remain separate from final corrected-binary certification. The controlled investigation above supplies separate failure/fix evidence, not retrospective attribution. Additional 100/250ms tracing probes passed without reproducing a miss. One diagnostic setup stopped before injecting keys during an ADB root transition; manual restoration was verified. These runs are not counted as final passing cases.

The audit also found that a cleanup exception could skip later restoration. Each action now runs independently, records its outcome and aggregates failures after all actions have been attempted. Four host fault tests cover failed release/clipboard, interruption and ADB restart/UID handling. Final records require all applicable cleanup checks to pass. Private palette preferences and clipboard are restored. Clipboard preservation uses a test-only focused activity and a private on-device parcel, never host/public clipboard content. Helpers release injected keys, restore exact preference/secure-setting bytes and viewport geometry, stop the app and restore unrooted adbd. Saved restoration assertions do not establish broader lifecycle or process-death safety.

Stills establish pictured states, not latency or continuous motion. Lenovo/ZUXOS, physical devices, alternate layouts, TalkBack, large fonts and endurance remain uncertified. Upstream runtime assets and notices remain unchanged. Two independent exact-head gpt-6.1-sol/high audits are required before merge.

## Reproduction

Use JDK21 and Android SDK from ANDROID_HOME or local.properties:

```sh
./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
python3 scripts/test-modifier-taps.py \
  --serial emulator-5590 --avd Droidcast_PR48_Tablet_API_36_27caf3 \
  --layout tablet --output artifacts/modifier-tablet
```

The default helper runs the ten new native cases. Use --layout phone with a fresh output directory after restoration. Final regression runs explicitly include ModifierTapDeviceTest, ModifierTapPaletteDeviceTest, ShortcutDeviceTest, SettingsDeviceTest and ShortcutReadinessDeviceTest with --expected 41. Run scripts/test-modifier-taps-hardware.py separately with the same explicit serial/AVD, a fresh output directory and --allow-adbd-root; that flag is restricted to the guarded emulator and is unnecessary for native tests or application use.
