# Validation record

This records observed checks, not a claim of complete Tinycast/Raycast parity or physical-device certification.

## Extended baseline: Android 0.3.1

Dedicated Android 16/API 36 arm64 emulator: 1920×1200 pixels, density 240, landscape tablet. Smaller-layout checks used 1080×2340, density 420.

| Check | Observed result |
| --- | --- |
| JVM command engine | 18 unit tests passed |
| Tablet regression suite | 20 tests passed, including two real Store packages |
| Continuous endurance | 21 complete rounds in 308.1 seconds |
| Settings dispatch | 21 launches, alternating the built-in command and installed-app search |
| Calculator / Actions | 21 verified clipboard answers and 21 Ctrl+K menu cycles |
| Document opening | 14 PNG and 7 PDF opens in the loop; TXT selected/opened through Android's chooser |
| Cold-restart documents | PNG, PDF and TXT reopened after stopping both the palette and viewer processes |
| Rotation | Four landscape→portrait→landscape cycles, with query preservation |
| Global shortcut | 20/20 actual Ctrl+Space activations: 10 from Settings, 10 from Home |
| System split-screen | Settings and Files paired through Overview, divider resized, palette shown/dismissed over both panes |
| Crash / ANR observations | No new crash-buffer entry; Android reported no ANR since boot |
| Phone configuration smoke checks | Four checks passed |

The earlier global-shortcut harness used root adbd only to inject Linux keyboard events on the dedicated emulator. The application itself ran with an ordinary Android UID and does not require root. Adbd was restored to its unprivileged state afterward.

Four instrumented-process PSS samples were approximately 98–103 MiB. The sampled palette task count remained one. This includes test overhead and is not a long-term leak assessment.

Initial harness setup failures were corrected before the successful endurance run: a moving document-picker drawer invalidated a cached node; text files had viewer handlers; and Android changed the chooser title after remembering a prior selection. They were not application crashes. An existing HTML Viewer kept an older overwritten fixture visible until its process was restarted; cold-viewer captures showed the current text.

## Publication preview: 0.4.0-preview

The preview updates visible branding to DroidCast and adds source/license access in Preferences. The package ID stays `dev.tinycast.android` so earlier previews can be upgraded. It does not add the features proposed in [ROADMAP.md](../ROADMAP.md).

The branded preview was rebuilt locally with 18 passing unit tests and 20 passing device regression tests, including both real Store examples. Android lint and workflow syntax validation passed. The renamed keyboard service also passed global Ctrl+Space activation from Settings and Home.

Publication checks and their results are recorded in the [preview release](https://github.com/moikdev/DroidCast/releases) and [Actions runs](https://github.com/moikdev/DroidCast/actions). The earlier 308-second run applies to the 0.3.1 implementation; it is not represented as a new endurance run on every subsequent branding build.

## Reproduce

Build/install/test commands and the dedicated opt-in endurance procedure are in [README.md](../README.md). Test sources are in [`app/src/androidTest`](../app/src/androidTest/java/dev/tinycast/android) and helpers in [`scripts`](../scripts).

The Store tests fetch and execute actual public bundles. They need network access; the document test needs its fixture. UUID Generator and OKLCH Color Converter are the only verified real Store examples. Other extension APIs, renderers and service integrations remain unverified unless stated explicitly.

The template in [`ci/android.yml`](../ci/android.yml) runs build, JVM tests and lint once enabled. It is not installed under `.github/workflows/` because the publishing OAuth credential lacks the `workflow` scope. It does **not** run the emulator endurance suite, install third-party extensions, or certify OEM global shortcut behavior.

Selected screenshots are in [`screenshots/`](screenshots). Local raw logs, private machine paths, caches, signing material and APK build outputs are excluded from the source repository.
