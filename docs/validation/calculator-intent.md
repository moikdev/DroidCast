# Deliberate calculator activation

Issue [#51](https://github.com/moikdev/droidcast/issues/51) refines root search: ordinary numbers should find commands, notes and web results without creating a calculator card. The calculator still works when the input expresses an operation. This does not complete calculator parity in #13.

## Behavior

- `123`, `12.5`, `.5` and `(123)` remain normal root searches. A numeric note title can be found and selected.
- Valid arithmetic with `+`, `-`, `x`, `X`, `*`, `×`, `/`, `÷`, powers or percentages calculates. Unary signs explicitly count as operators, so `-12` and `+12` calculate.
- `12x3`, `12*3` and `12×3` all show **12 × 3 → 36**. The search field retains the input exactly; Enter copies only `36`.
- ASCII x is consumed by the arithmetic parser at a multiplication position. Ordinary words, unknown identifiers and incomplete expressions remain searches. No global replacement inside search text is performed.
- Selecting Calculator or typing `=` deliberately enables single-number calculations. Existing functions, constants `pi`/`e`, fixed unit conversions, precedence and numeric output remain available.
- Category filters still apply. The calculator is not injected into Apps or Pinned results. Root results evaluate the query once and reuse the outcome for the calculator card and web fallback.

[Raycast's calculator](https://www.raycast.com/core-features/calculator) uses expression/result columns and copies the answer with Enter. This slice preserves that presentation while implementing the user's stricter number-search preference. It does not add Raycast's dates, time zones, natural-language expressions or currency services.

## Local evidence

The paired APK, screenshot/XML and test records are in [calculator-intent.json](calculator-intent.json). All screenshots are unedited Android captures under `docs/screenshots/parity-calculator-intent/`. They identify baseline versus final builds and tablet versus phone views. PNGs alone do not establish latency or continuous animation.

Final local results: successful build, **87 JVM tests**, **13/13 tablet + 13/13 phone cases**, and lint **0 errors/42 warnings**. Eight new calculator cases and five existing integration regressions run per layout. All 26 PNGs match their raw captures and paired native XML records. Two original-build negative cases fail as expected. Installed application bytes match the final APK on both layouts.

Validation uses the independently owned API36 arm64 emulator `Droidcast_PR48_Tablet_API_36_27caf3`, serial `emulator-5590`: physical 1920×1200/240dpi and a 1080×2340/420dpi phone override. The helper requires the exact AVD name, an emulator serial and unprivileged adbd. Application code and tests do not enable Accessibility or request new permissions.

Before captures use the final PR #50 APK (`dafaeeba…b3b0a`), whose reviewed source tree equals base merge `7bec152`. They show the unwanted numeric card, old `*` display and absent x calculation. The same native regressions fail against that original app, then run against the final build. The baseline capture helper is separate from the final test class and asserts the original behavior explicitly.

Native final coverage includes number/note/web search, decimal and parenthesized numbers, multiplication variants and clipboard, arithmetic precedence, explicit Calculator command, ordinary x words and incomplete expressions, deletion back to search with unchanged palette/search geometry, functions/conversions/category filters, and actual injected x/Enter events while search is focused. Existing calculator copying/conversion, calculator shortcut mode, rigid palette typing and immediate emoji navigation regressions also run on each layout.

Capture checks first assert the selected Compose result, then require the same editable query and expected result text in Android's native hierarchy before saving the PNG. Root palette test tags are Compose-only, so XML proof uses the native editable field and visible app text rather than nonexistent native resource IDs.

The first baseline capture attempt is retained and excluded: its assertion incorrectly expected Compose test tags in native XML. The hierarchy actually contains the correct numeric query and calculator labels. The corrected capture requires those structural native states without changing product code.

An earlier final-tablet attempt completed all 13 tests, but ADB briefly went offline while transferring its screenshot archive. The transfer was truncated and its host cleanup was not verified. That attempt is excluded from final evidence. The same emulator process remained running and recovered without restart; the helper now retries idempotent transport operations, checks individual artifact byte counts and retains a private ignored preference backup for recovery. Final runs use fresh evidence directories. This transport failure does not establish an app defect or an independently diagnosed ADB cause.

The helper verifies installed APK bytes, restores exact original palette preference bytes after force-stopping the app, restores phone geometry, and checks that keyboard consent/IME settings are unchanged. Each new native test also restores its palette values and clipboard. The recorded regression cases are scoped local checks, not a full application, physical-device or OEM certification. CI remains inactive; `local/android` reports local evidence only. Upstream assets and notices remain unchanged.

## Reproduction

Build using JDK21 with Android SDK from `ANDROID_HOME` or `local.properties`:

```sh
./gradlew assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug
python3 scripts/test-calculator-intent.py \
  --serial emulator-5590 --avd Droidcast_PR48_Tablet_API_36_27caf3 \
  --layout tablet --mode final \
  --apk app/build/outputs/apk/debug/app-debug.apk \
  --test-apk app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk \
  --output artifacts/calculator-final-tablet
```

Use `--layout phone` and a fresh output directory for the phone run. Baseline mode requires the recorded original APK; negative mode requires the same original app with the final test APK and expects the two selected regressions to fail. Failed runs remain separate from final evidence. Two independent exact-head `gpt-6.1-sol/high` audits are required before merge.
