# Rigid palette while typing

Implements #46 and #49. Baseline source: `b974ff0a5eff13d18314fb6667440c1682588e54` (same tree as reviewed emoji source `679c69e`). This is a native Android palette change; vendored runtimes and system animation settings are unchanged.

## Behavior

The command and emoji search origins use the system-bar/cutout safe viewport, independent of keyboard height. Opening the IME reduces the results area beneath the fixed search header; hiding it restores that space. The layout uses the IME destination inset rather than following each keyboard-animation frame. The system keyboard itself retains Android's animation.

Query/result changes request the selected row in the next lazy-layout measurement, replacing the old keyed scroll anchor. An already fully visible selection does not scroll when arrow keys move to it. Offscreen keyboard selections still snap into view. Manual list scrolling remains available. Caret/selection/composition-only command-search edits retain their TextFieldValue without running search again. Derived results are cached across unrelated UI recompositions; there is no debounce or typing delay.

Copy feedback uses a keyboard-safe bottom inset so it remains visible above the IME without moving the search header.

Explicit tablet keyboard requests now persist through edits; the old delayed automatic hide is removed. The final typing case uses the focused Android InputConnection, requires a docked IME covering more than one quarter of the display after each edit, invokes the calculator through the real command executor (without an IME action that can dismiss its own keyboard), and requires visible copy feedback above the still-visible IME. Earlier weaker copy checks and strengthened-check failures are not final success evidence.

The emoji picker also waits for its replacement text-input focus before hiding an inherited tablet IME. Its explicit Show on-screen keyboard control remains available.

## Immediate emoji keyboard navigation

Opening Emoji keeps text input ready while plain four-way arrows and Home/End select results immediately; Enter copies the highlighted glyph. Ctrl+L explicitly enters query editing, and modified caret keys also enter editing before the IME receives them. Up/Down return to browsing. Typing filters in either mode; Up/Down or Escape explicitly leave query editing. Active composition passes navigation to the IME. No focus-switching popup or manual Tab is needed. A native test types `face` using hardware key events, traverses all directions and Home/End, deletes to `fac`, navigates again and verifies the exact copied glyph without leaving search focus. Existing caret/composition coverage now explicitly enters query editing.

Two additional regressions dispatch events in one main-thread turn, without a redraw between them. On source `3366c2e`, Right→Enter copied the previous emoji, and beginning composition then pressing Right moved the result selection. Both fail in the saved negative run. Activation/favorite handlers now resolve the current result and composition at event time; the focused correction and final suites include both cases.

## Evidence and scope

The original four regression cases were run against rebuilt, unchanged baseline production code: all four failed. Native observations include command search top **255 → 72 px**, emoji search **329 → 146 px**, a partially clipped first row after selecting the already visible second result, and selected command **1 → 0** on caret selection. Before screenshots show those actual states; they are not mockups or copies from a different feature.

Final run counts, exact APK/test APK identities, screenshot hashes, coordinates and local checks are recorded in [rigid-palette.json](rigid-palette.json). Final local checks pass: **71 JVM**, **23 tablet** (6 stability +17 regressions), **22 phone** (5 stability +17 regressions), build/lint **0 errors/41 warnings**, and matching installed APK bytes. The extra default-emoji-entry case is executed only on tablet.

The five principal native cases cover keyboard show/hide with typing, insertion/deletion/calculation and visible copy feedback above the IME, empty Apps results, first rendered query layout from a scrolled viewport, visible arrow selection and caret selection. The additional tablet-entry case checks entry with an inherited keyboard; the phone branch is explicitly inapplicable. Existing emoji, compact/translucent palette, immediate-search, calculator/conversion and arrow/pin tests are also rerun on both viewports.

An initial broader tablet run failed the nine emoji setup checks while waiting for the default hidden keyboard. Its log is retained; it is not counted as a passing validation. The focus-aware hide correction and subsequent final runs are recorded separately. The baseline four failures remain intentional negative controls.

The final pipeline temporarily enables Android’s `show_ime_with_hard_keyboard` preference on the guarded emulator to prevent Gboard’s zero-inset compact toolbar from making the geometry check vacuous. Where Gboard first shows its compact toolbar, the test also activates Gboard’s own “Show on-screen keyboard” control. Native assertions require the IME to cover more than one quarter of the display, including after every native text edit and when checking copy feedback. The original preference is restored in a host `finally` block and recorded in the manifest. This preference change is test setup only; the application does not change Android settings.

Screenshots are real, unedited API 36 arm64 emulator captures: Tinycast_Tablet_API_36, tablet 1920×1200 at 240 dpi, and phone override 1080×2340 at 420 dpi. Screenshot filenames distinguish before/final and viewport. Before and final source/APK identities are distinct. Geometry assertions and first-layout tests establish movement behavior; screenshots are visual smoke evidence, not latency measurements or a video of every animation frame.

CI remains inactive; local/android represents local checks. This slice does not certify physical Lenovo/ZUXOS, other OEMs/API levels, custom IMEs, large fonts/TalkBack, small landscape multiwindow, extension webview animation or end-to-end latency. Existing #35/#37/#41 remain open. Keyboard readiness #39, actual assistant touch gestures #47 and double-tap Ctrl #26 are separate follow-ups.

Primary API references: [safeDrawing includes IME](https://developer.android.com/reference/kotlin/androidx/compose/foundation/layout/package-summary#safeDrawing), [IME destination inset](https://developer.android.com/reference/kotlin/androidx/compose/foundation/layout/package-summary#imeAnimationTarget), and [requestScrollToItem replaces the keyed anchor for the next measure](https://developer.android.com/reference/kotlin/androidx/compose/foundation/lazy/LazyListState#requestScrollToItem(kotlin.Int,kotlin.Int)).
