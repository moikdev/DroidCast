# Raycast-style settings and selected-folder validation

Development build after the original `v0.4.0-preview` tag, based on main `6ea2b328c6d860d4944c94318649fecc058b5042`. This implements #36 and advances #5; it does not establish full Tinycast/Raycast parity. Only implemented settings are shown.

## Device and build

Dedicated `Tinycast_Tablet_API_36`, Android 16/API 36, arm64, 6 GiB RAM. Tablet viewport 1920×1200 at 240 dpi; phone viewport 1080×2340 at 420 dpi on the same AVD. Final validation uses host GPU/Metal with Vulkan disabled. These are emulator layouts, not physical tablet/phone certification. Phone overrides are restored in `finally` blocks.

The application runs under its normal Android app UID. Only the separately guarded Linux hardware-key helper temporarily roots adbd; it restores preferences, Android service consent, released keys and unprivileged adbd before returning. No broad storage permission is added. CI remains inactive; `local/android` describes local validation.

The build identity and final results are in [settings-checks.json](settings-checks.json). Local raw build/instrumentation logs, native XML and private restoration snapshots remain under ignored `artifacts/`; private snapshots are not published.

Final targeted runs pass 15/15 on tablet and 15/15 on phone; the preceding complete suites pass 71/71 on each. Final source build/lint and 64 JVM tests pass. Hardware delivery passes 16 cases on the pre-correction APK, with restoration verified. APK identities are explicitly separated in the JSON.

A separate manual tablet rotation check changes landscape → portrait (1200×1920) → landscape, preserves the selected global-shortcut inspector and Ctrl+Space value, and verifies the stored shortcuts are unchanged. The original free-rotation mode is restored. Its portrait PNG is included; this is an emulator check, not physical-device certification.

## Checked behavior

- Separate, non-exported settings activity; compact tablet window/sidebar, grouped preference rows, categorized command table, alias/hotkey columns, category and Assigned filters, inline inspector and phone command details.
- Ctrl/Meta+, entry point, Ctrl/Meta+Shift+, selected-command configuration, Actions routes, settings search, alias-field focus and fresh palette return. Existing global assignments are preserved; navigation takes contextual precedence inside DroidCast.
- Inline shortcut conflicts, reset, clear, cancellation, background cancellation, saved binding persistence and recreation. Tab/Shift+Tab preserve candidates. Enter, keypad Enter and D-pad Center are checked on DOWN and UP, with and without Shift. Existing command dispatch, replay/expiry and backup-binding regressions remain covered.
- Alias conflict feedback and successful persistence. The Done action dismisses the software keyboard and attempts save, retaining invalid text and showing errors inline. Tab/Enter activates Save. Short-height layout reduces chrome and makes sidebar navigation scrollable while the IME is open.
- Inline backup preview, review, cancellation and unchanged data, with real Android document pickers. Picker cancellation waits for the picker to actually close; Back can first navigate to a parent directory.
- Real ExternalStorageProvider tree selection, persistent read grant, nested filename/path metadata index, MIME filters, unknown-MIME recent-document exclusion from narrow filters, superseded query results, stable IDs, store reopening, corrupt-cache recovery, disable/cache deletion, expiry/reauthorization and scope removal. Recent-document entries remain separate. Indexed document references are excluded from portable backups.
- File search evidence requires the rendered `Orbit` query and absence of unrelated archive results before capture. The selected URI's actual text is asserted before dispatch; the native HTML Viewer package, title and WebView are checked. **Rendered body verification requires visual review of `opened-document.png`.** This stock viewer sometimes omits its body from accessibility XML despite displaying it. `settings-viewer.json` records whether an automatic body match was also available; it is not substituted for the screenshot review.

A warm-viewer regression first opens the indexed TXT as the viewer task root, then puts another fixture document above it. Opening the TXT from actual file search must replace that stale top and show the requested title. File VIEW intents combine `CLEAR_TOP` with `NEW_TASK`; the selected URI and temporary read grant remain unchanged.

Metadata listing uses one bounded provider worker/queue, cancellation/time limits and a bounded private cache. Index construction and search run away from the UI thread. These bounds are source/JVM checks and a small real tree workflow; they do not certify large real or cloud providers.

## Independent review corrections

The first audits reviewed `381e95f`: UX had no blockers and found stale alias feedback (P3); correctness requested cancellation-safe SAF grant ownership (P2). The correction uses a shared grant transaction: acquisition bookkeeping runs beside acquisition inside IO, and ownership transfers beside the durable configuration write under the disk mutex. Prompt cancellation while returning from either dispatcher boundary cannot skip the bookkeeping. Uncommitted new grants are released under a noncancellable cleanup context; preexisting grants are retained. Cancellation after commit, including the subsequent permission read, preserves the saved folder's grant. Removing an owned scope also performs its release beside the successful configuration write, before a cancellable return; failed or cancelled-before-write removals retain the existing grant. UI change-state cleanup also runs even if cancellation is rethrown.

Eleven deterministic JVM tests use queued coroutine dispatchers to cancel at the exact return boundaries, plus before acquisition, after commit, with a preexisting grant, on validation/write failure, and across removal commit/cancellation/failure. A negative control that moves bookkeeping back outside the IO returns fails all three boundary tests; restoring the correction passes all eleven. This is a controlled source mutation test, not an on-device timing reproduction or a process-death guarantee.

Alias Clear now uses the successful-save path, clears validation feedback only after the store accepts the change and displays “Alias cleared.” The native regression covers conflict → Clear, empty editable value despite its placeholder, no error, persisted empty alias, and a subsequent successful save. Tablet/phone `alias-cleared.png` captures show that state. Final focused settings/route/folder runs exercise these review corrections; the earlier full-suite and hardware binaries retain their separate identities.

## Screenshot provenance

All committed images are unedited, real Android PNGs. PR image URLs pin the implementation commit.

- `parity-settings/before-popup.png`: copied byte-for-byte from PR #33's committed manager capture; baseline production source is the main base above.
- `parity-settings/before-tablet.png`: the earlier, uncommitted settings layout captured during this branch, before the user's tablet refinement request. It is explicitly WIP history, not a released build or the final implementation.
- `parity-folders/before-search.png`: baseline palette before folder commands/indexing, captured from PR #33's verified APK (same production source as the base).
- `parity-settings/tablet/` and `phone/`: final settings configuration, filtered commands, recording/conflict, alias error and backup states. Saved UI/model assertions precede capture.
- `parity-folders/`: actual folder chooser, configured scope, verified query/results, external viewer, expired access and removed scope. Viewer title/body are personally checked in the full-resolution PNG, in addition to the automated URI/title checks described above.

## Iteration findings and limits

The initial full-screen, widely spaced settings design was replaced after user feedback. Native testing found alias focus being stolen by initial container focus, offscreen action-menu selection, and IME resize/save-feedback problems. The final implementation preserves requested focus, scrolls keyboard action selection into view, supports compact IME layout/Done saving, and retains inline error feedback. Tests wait for rendered focus/error states rather than assuming the first composition has settled. Raw hardware testing then exposed Ctrl+F failing after a touch-saved shortcut left no editor focused. Settings now handles contextual navigation at the activity boundary, with regressions for container focus, Escape and reset-cancellation depth. Final source review also identified that reset confirmation replaces the Record button; focus restoration must skip that absent control. The final targeted regression starts reset during an active recording, then verifies cancellation preserves the prior chord and confirmation resets it. Full-suite and hardware runs use the an earlier APK; the focus guard plus a revoked-folder count correction and their regressions are separately validated in the final targeted runs. Screenshot review caught that revocation hid entries but left the previous status count; revalidation now reports the remaining count and the test requires zero. File-search capture also waits for the settled indexed status. Asynchronous file-filter assertions synchronize through Compose before native accessibility checks; the status uses the unmerged semantics tree because the palette merges its children. The earlier native-only and merged-tag waits failed despite a later diagnostic showing the expected status. See [Compose synchronization](https://developer.android.com/develop/ui/compose/testing/synchronization). Their APK hashes are distinguished in the JSON. All final after screenshots are recaptured on the final APK. Category selection dismisses the IME; its test waits for the popup to be rendered before selecting a category.

One complete 71-case tablet run passed 70/71: the correct indexed URI was dispatched, but the warm viewer task surfaced its previously opened top document. Saved task state showed that document above the requested root. The fix adds `CLEAR_TOP`, and the native regression now establishes this warm stack explicitly before opening the requested file and asserting its title.

An older 3 GiB emulator session developed System UI ANRs and Store connection failures. A software-renderer process later exited. Those runs are retained locally as superseded/failed evidence. A cold start with 6 GiB and host rendering was used for final validation; this is not an application fix or a demonstrated diagnosis of every earlier failure.

A complete hardware behavior run passed all 20 cases but the helper exited during the unroot transport reply. Direct recovery checks found adbd UID 2000, the original preference bytes intact, and Android service consent restored to disabled. The helper now waits for reconnect and verifies the actual UID even if the unroot reply is nonzero, and explicitly asserts restoration of both consent settings. Final hardware results in the JSON identify the repeat count; this harness correction is not an application change.

The original phone overlay assertion required tablet width; it now checks compact tablet bounds or phone side margins as appropriate, while preserving real outside-tap dismissal and translucent-window assertions.

Remaining #5 work includes cloud/offline providers, providers that ignore cancellation, very large real trees, moved/deleted-file workflows and process-death edge cases. Reopening a store/cache is not claimed as a process-restart test. Comprehensive TalkBack, large-font, physical-device/OEM/layout delivery and advanced #26 hotkeys remain unverified. The previously unexplained phone backup timeout remains separately tracked in #35. One intermediate tablet run passed 68/70: tile activation timed out, followed by a settings screenshot timeout. Two focused tile/settings reruns passed 2/2 each. Notification-shade cleanup now isolates subsequent cases, but no cause or tile fix is established; #37 tracks this limitation.

## Reproduction

Use the dedicated AVD and a JDK 21 installation; retain the existing document fixtures required by the older palette/endurance tests.

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
adb -s emulator-5580 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5580 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5580 shell am instrument -w -r -e notClass dev.tinycast.android.EnduranceDeviceTest dev.tinycast.android.test/androidx.test.runner.AndroidJUnitRunner
python3 scripts/test-configurable-shortcuts.py --adb "$ANDROID_HOME/platform-tools/adb" --serial emulator-5580 --rounds 3
```

Phone validation applies `wm size 1080x2340` and `wm density 420`, then resets both even on failure. The opt-in five-minute endurance test is not part of this slice's full instrumentation invocation; its older validation remains version-scoped in the existing testing record.
