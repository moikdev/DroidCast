# Backup and restore validation

Enhancement [#3](https://github.com/moikdev/DroidCast/issues/3). Baseline `58ea4d1`, after merged settings PR #23. Upstream comparison: Tinycast `37e6043`, `docs/features/backup.md`. [Format and user workflow](../BACKUP.md).

## Local checks

- App/test APK build and Android lint: pass.
- JVM tests: **30 passed**, including 9 backup planning tests.
- Tablet device tests: **36 passed** (7 backup + 29 existing palette/overlay/settings regressions).
- Phone-layout checks: **7 passed**, including real document-picker round-trip/cancellation, preview rotation, replacement, validation, alias conflict and maximum ranking.
- Final APK SHA-256: `739b90f221df2acf7d8ace93c2f9dcaff2d94beea794a2608dcca84e4aa5432e`.
- Dedicated Android 16/API 36 arm64 emulator. Tablet 1920×1200, density 240. Phone configuration 1080×2340, density 420; restored to tablet after checks, adbd/app unprivileged. No physical-device/OEM certification is claimed.

## Coverage

The real Android document picker exports a uniquely named JSON file, selects that same document for import, verifies its note/quicklink/clip content and merges it without duplicates. A new store instance reads the restored collections. Category replacement keeps unrelated collections, document grants and accessibility state. Preview cancellation, picker cancellation and malformed input preserve stored data; preview also survives Activity recreation.

Codec checks cover current-version round-trip, selected-category absence, UTF-8, malformed input, duplicate fields and item identities, unsupported versions/fields, trailing data, deep nesting, oversized input, invalid links, nested Android activity references and excluded document references. Planner tests cover merge idempotence, content deduplication with reference remapping, alias conflicts, remapped alias collisions, duplicate ranking, category replacement and clip limits. An imported maximum ranking value stays nonnegative when the next command is used.

The existing palette/overlay/settings tests exercise notes, clips, quicklinks, aliases, keyboard actions, files, app search, tile entry, dismissal and all 11 dedicated Settings pages. The extension runtime is unchanged; real Store installation tests were not repeated in this slice.

## Screenshot evidence

Real unedited emulator framebuffer output under [parity-backup](../screenshots/parity-backup). Before search is from baseline `58ea4d1`; after captures come from the changed app. The helper waits for the expected native UI marker and window idle before capture; PNGs are visually inspected before publication.

Evidence includes category selection, Android export picker, import preview, content inspection, replacement confirmation, restored note search, duplicate summary, alias collision and malformed-file feedback. Phone screenshots are labeled separately.

## Earlier harness iterations

An assertion initially requested exact equality for a substring; a cancellation assertion matched both the search text and dialog title. Both were corrected to specific checks before successful revalidation. Screenshot inspection caught a preview capture before the pixels had caught up with Compose semantics; capture now requires native UI evidence and idle. Tall dialogs were constrained to keep their scrollable content clear of navigation controls. Earlier failed runs are not counted as passing checks.

## Boundaries

The format is DroidCast JSON v1, not Tinycast AppleArchive or a Raycast export. Those container readers remain open in [#24](https://github.com/moikdev/DroidCast/issues/24). Backup/restore parity remains partial. Backups are readable plaintext; the app explains this before export. Credentials, extension data/code, recent-document URIs/grants and accessibility consent are excluded. Import grants no capabilities and runs no commands.

GitHub CI is inactive because the publishing credential lacks workflow scope; these are local checks, not CI results.

```sh
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest :app:lintDebug
adb shell am instrument -w -r -e class dev.tinycast.android.BackupDeviceTest,dev.tinycast.android.PaletteDeviceTest,dev.tinycast.android.PaletteOverlayTest,dev.tinycast.android.SystemSettingsDeviceTest dev.tinycast.android.test/androidx.test.runner.AndroidJUnitRunner
```
