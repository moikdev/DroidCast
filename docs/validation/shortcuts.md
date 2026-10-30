# Configurable shortcuts validation

Enhancement [#4](https://github.com/moikdev/DroidCast/issues/4), PR [#33](https://github.com/moikdev/DroidCast/pull/33). Baseline `77cb9ad` after backup PR #25. Upstream Tinycast `37e6043`, `docs/features/hotkeys.md`: persistent bindings, owner-aware conflicts, shared command dispatch, keycap labels, pausing during recording and palette-mode toggling. Advanced modifier-tap/Fn/Hyper parity remains open in #26.

## Local verification

- Build, test APK and lint: pass; **46 JVM tests** pass (11 shortcut, 14 backup planner, 18 command-engine, 3 Settings).
- Full tablet run: **51 tests pass**, including 12 shortcut, 10 backup and 29 palette/overlay/Settings checks.
- Phone layout: **22 tests pass**, all shortcut and backup checks.
- External Linux-keyboard matrix: **18 cases pass**, including six summon/toggle trials, real command destinations, unrelated typing and reconnect.
- APK SHA-256: `d0b73918d045e8975c8d93496f2ef7c718f2f00f99412d995d4c12edd7974576`; installed APK checksum matches. All after captures and final checks use this APK.
- Tablet dimensions restored; hardware helper asserts original preferences restored and unprivileged ADB/app UID.

Sanitized records: [device checks](shortcuts-device.json), [hardware matrix](shortcuts-hardware.json). Raw logs stay in ignored local artifacts. GitHub CI remains inactive; `local/android` denotes locally executed checks, not CI.

## Coverage

JVM regressions cover chord rules, unique ownership, exact modifiers, plain typing, repeats, paired release consumption, device isolation, lost-release recovery, recorder pause, bounded single-use invocation tokens and expiration. Backup planner regressions cover existing files without the new category, unselected bindings, content-deduplication remapping and conflicts with preserved document bindings under merge and replace.

Device checks cover real recorder keys, Tab/Shift+Tab focus movement with candidate preservation and Enter saving, existing editor focus, local command activation, result keycaps, clear/reset, named conflicts and disabled Save, consent preservation, leaving the recorder, corrupted saved configuration, replay/stale/reassigned/forged invocations, unavailable commands, selected backup confirmation and document-binding exclusion. Calculator typing produces 84 for 12*7, replacing the middle selection produces 21 for 3*7, and a second shortcut press closes its existing mode.

The hardware helper injects Linux keyboard events outside UiAutomation; instrumentation key injection alone is not evidence of accessibility filtering. The helper waits for both the bound service and Android's installed input filter after UI inspection; toggle checks wait for the destination rather than assuming immediate dismissal. It checks recording pause, a changed global binding, repeated show/hide from Settings and Home, the old chord ceasing to activate, named conflicts, Wi-Fi and Files destinations, persisted TXT viewing, calculator arithmetic/toggle, note editing, repeat suppression by usage count, unrelated typing and service disconnect/reconnect. It preserves existing service configuration and private palette preferences in ignored local artifacts, restores them in `finally`, releases injected keys and restores unprivileged adbd. The APK runs under its ordinary app UID throughout.

Hardware testing exposed a real cursor bug: after programmatic calculator entry, typing produced `12*7= ` because the search field retained its previous cursor. A device regression reproduced the missing result. The search field now carries selection/composition explicitly, puts the cursor after programmatic text, and preserves user-selected replacement. The focused regression passes.

The subsequent full run passed 48/51: the external keyboard test had left Settings Intelligence search open, exposing root Settings returning to that warm task. [#34](https://github.com/moikdev/DroidCast/issues/34) adds CLEAR_TOP to the root command, a regression that opens native search first and verifies root-specific UI afterward, and explicit root navigation in overlay fixtures. The destination is captured as `root-settings.png`. A first capture helper incorrectly accessed a finished Activity; it now uses target application context.

The earlier combined run passed 49/50 tests; its failure was a picker fixture outside the visible file list. Follow-up harness runs exposed a stale Downloads node and a query-field selector mismatch. The corrected picker checks the Downloads root, searches for the fixture and selects its actual title row rather than the search field. A focused rerun passed. The initial hardware run also stopped at a remembered HTML Viewer chooser whose title already contained the selected app; the helper now treats the separate app row as optional before choosing Just once. Its finally block restored preferences, consent and unprivileged adbd even on that failure. Another assertion was corrected to accept Settings Intelligence as the native search host and verify the same host/text after an unbound chord. Those failed iterations are not counted as successful verification.

## Independent UX audit remediation

The gpt-6.1-sol/high UX audit of `adb29c8` identified a P2: Shift+Tab was consumed as an invalid shortcut, clearing the candidate and disabling Save. The extended recorder regression reproduced this before the fix. The recorder now lets Shift+Tab reach normal focus traversal. The test requires Save → Shift+Tab → Cancel focus, unchanged candidate, no validation error, enabled Save, then Tab → Save → Enter and persisted binding. `backward-focus.png` captures the preserved candidate and focused Cancel. Universal keyboard/TalkBack certification remains unclaimed.

The audit's P3 coverage finding is also addressed: the external TXT case now verifies both the fixture title and distinctive body text in the viewer before recording success or capturing its PNG. A local XML capture preserves those native UI assertions for audit. Merely foregrounding HTML Viewer is insufficient.

## Evidence and limits

**20** real, unedited emulator framebuffer PNGs live in [parity-shortcuts](../screenshots/parity-shortcuts). The before capture is the fixed shortcut help at baseline `77cb9ad`. After captures show the manager, recorder, assigned result keycap, conflict, reset and backup warnings, and hardware-launched command destinations. Expected native UI is checked before capture, then PNG contents are visually inspected.

The dedicated API 36 arm64 AVD uses tablet 1920×1200/density 240, with phone-layout checks at 1080×2340/density 420. Tablet geometry is restored afterward. No physical keyboard/OEM, all-layout, TalkBack or universal Tab traversal certification is claimed. Android owns accessibility consent; importing bindings never enables it. Existing extensions were not changed or rerun in this slice.

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
adb shell am instrument -w -r -e class dev.tinycast.android.ShortcutDeviceTest,dev.tinycast.android.BackupDeviceTest,dev.tinycast.android.PaletteDeviceTest,dev.tinycast.android.PaletteOverlayTest,dev.tinycast.android.SystemSettingsDeviceTest dev.tinycast.android.test/androidx.test.runner.AndroidJUnitRunner
python3 scripts/test-configurable-shortcuts.py --serial emulator-5580 --rounds 3
```
