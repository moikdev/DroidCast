# Offline Emoji & Symbols validation

Scope: #11's native grid, local English search, category filters, favorites,
recent copies, keyboard navigation, exact Unicode clipboard delivery and restart
persistence. The source references and remaining advanced parity are in
[EMOJI.md](../EMOJI.md) and [#44](https://github.com/moikdev/droidcast/issues/44).
No direct cross-app paste, multilingual search, skin-tone controls, current/full
Unicode coverage, portable favorites, physical/OEM or TalkBack certification is
claimed. CI is inactive; `local/android` reports local checks only.

## Device and build

Dedicated Tinycast_Tablet_API_36, Android 16/API 36 arm64. Tablet 1920×1200 at
240 dpi; phone override 1080×2340 at 420 dpi. App package remains
`dev.tinycast.android`, version 0.4.0-preview (5). The machine-readable
[manifest](emoji-picker.json) records the app/test APK SHA-256 values, installed
APK equality, screenshot hashes/dimensions and restored device state. The APK
runs without root or new Android permissions. The tests do not enable the
keyboard accessibility service.

Before evidence is the installed fullscreen-nightly r2 APK, SHA-256
`fe44ce5611ccb22e8634baaddfd19dcdc6a155aafb5a3117695d58ae9eded04c`,
source `0ac6689253c0d15c95c014bd72cd56a8829ef144` (same source tree as base
`34dd0cd4e1f1ee7fbe836d8475ee92f0b6f4797e`). Searching `emoji` only offers
web search. This is a real pre-change capture, with the software keyboard open;
it is not a geometry comparison with the after captures.

## Checks

- Build, lint and all 71 JVM tests (five new catalog tests). Tests read the actual
  bundled catalog, check search/ranking/filtering, history bounds and exact
  identities/sequences. Generator output reproduces the committed TSV.
- Ten instrumentation checks on each layout: eight EmojiDeviceTest cases plus
  existing palette compactness/transparency/dismissal and immediate-search cases.
  These cover the command entry point; named/alias/keyword search; category and
  empty states; favorite clear/reuse and activity recreation; five exact Unicode
  clipboard strings; Copy and IME actions; keypad Enter, Enter, Ctrl+Enter,
  Ctrl+keypad Enter and D-pad Center; all four arrows, Home/End; focus return and
  Escape. A regression navigates while search retains focus, moves the caret,
  selects text and applies a composition-only update through the actual Android
  input connection, then verifies the copied glyph and retained copy feedback.
  The tablet keyboard-show control opens the IME while keeping search focused;
  the phone branch verifies that the tablet-only control is absent.
- Paused Compose-clock checks require the selected end-of-grid cell to be visible
  after two layout frames and preserve the palette frame across query changes.
  Intentional IME/focus transitions are settled before measuring typing geometry.
- A host-driven force-stop/relaunch checks persisted favorites and recents in
  airplane mode. It restores preferences and airplane mode in `finally`.
- Final adb identity, viewport reset, installed APK equality and evidence hashes
  are recorded in the manifest. Clipboard state is restored by instrumentation;
  the isolated host restart smoke leaves the copied test family emoji on the
  emulator clipboard. No unrelated user content is included in evidence.

Local raw evidence: `artifacts/emoji-build.log`, `emoji-test-build.log`,
`emoji-{tablet,phone}.log`, `emoji-{tablet,phone}-evidence/`,
`emoji-restart.log` and `emoji-restart-favorite.xml`.

## Corrections exercised before the final runs

An initial clickable palette container merged otherwise separate accessibility
labels; it was replaced with a pointer handler that consumes background taps
without merging descendants. Initial native failures are retained in
`artifacts/emoji-initial-tablet-failure.log`.

The initial keypad-Enter regression failed while search retained focus; the
regular preview handler never received that key in the temporary emulator probe.
The final narrow pre-IME handler copies only on the first key-down, consumes the
pair/repeats and leaves active text composition alone. Temporary logging was
removed. Saved failure records are under `artifacts/emoji-negative/`; final
tablet/phone runs test real injected keyboard events and exact clipboard output.
An intermediate test also incorrectly assumed the plain text infinity sign
would outrank the equally named emoji. The final test explicitly selects Text
symbols rather than changing legitimate catalog ordering.

The first host restart harness proved the visible persistence state but stalled
while restoring preferences through `adb exec-out ... tee`, truncating the
isolated emulator's palette preference file. That harness result is not the final
restoration evidence. The emulator fixture baseline was restored from the latest
archived hardware-test preference snapshot, with empty pre-feature
emoji preferences and airplane mode off. The corrected helper snapshots files to
disk before running, restores through `adb shell` input redirection, and checks
byte equality. The final native suites and host restart run follow that recovery.
One subsequent phone run was interrupted by the host restart helper being started
too early; it is discarded as a harness scheduling error. Its raw log is retained
as `artifacts/emoji-phone-interrupted.log`. The final phone rerun and host restart
are executed sequentially.

## Screenshots

All PNGs live in `docs/screenshots/parity-emoji/` and are embedded with
commit-pinned URLs in the implementation PR. Tablet and phone after captures
are extracted byte-for-byte from native test output. Tests dismiss Android's
temporary clipboard-preview bubble through its real Dismiss control before
capturing copy feedback so it does not cover the footer; the host restart capture
comes from the verified Favorites state after force-stop. The manifest records
every PNG. Captures show the grid, text symbols, alias search, copied family,
favorites, recent copies, empty favorites, no results and keyboard-copy feedback.
The visible underlying Settings/Home and system keyboard belong to the emulator.

## Audit corrections

The first UX audit at `72e0bdc` found that selection-only `TextFieldValue`
updates called `search()` and reset the grid selection. The focused regression
fails against that implementation (`artifacts/emoji-negative/caret-reset.log`).
The correction always retains the field value but searches and clears copy
feedback only when the text changes. Coverage now explicitly asserts Up, Right
and Home as well as the previously exercised directions, and the tablet's
keyboard-show path.

The old tablet no-results XML contradicted its paired PNG (stale count/name and
enabled actions). This establishes an evidence inconsistency, not its cause or
a product/TalkBack failure. The old XML is retained in
`artifacts/emoji-negative/stale-no-results.xml`. Capture now clears the native
accessibility cache where supported, dumps the hierarchy and requires its tagged
count, selected footer name and Copy/Favorite enablement to match the expected
state before taking the PNG. Each layout starts with a fresh evidence directory
to avoid carrying tablet-only captures into phone evidence. All final pairs are
recaptured; comprehensive accessibility certification remains unclaimed.
