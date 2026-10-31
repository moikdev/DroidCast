# Parity implementation loop

The live backlog is [GitHub issue #1](https://github.com/moikdev/droidcast/issues/1). Every implementation PR includes real Android screenshots and independent `gpt-6.1-sol` / `high` correctness/security and UX/test audits before merge. See [AGENTS.md](../AGENTS.md).

## Enhancements

- [#2: Open dedicated Android settings destinations from palette commands](https://github.com/moikdev/droidcast/issues/2)
- [#3: Add versioned user-controlled backup and restore](https://github.com/moikdev/droidcast/issues/3)
- [#4: Add configurable global and per-command keyboard shortcuts](https://github.com/moikdev/droidcast/issues/4)
- [#5: Search user-selected folders with persistent document grants](https://github.com/moikdev/droidcast/issues/5)
- [#6: Render native extension Grid, Markdown, metadata and complete Forms](https://github.com/moikdev/droidcast/issues/6)
- [#7: Expand verified Raycast host compatibility and capability reporting](https://github.com/moikdev/droidcast/issues/7)
- [#8: Support multi-argument quicklinks, variables and Android deeplinks](https://github.com/moikdev/droidcast/issues/8)
- [#9: Add reusable snippets with variables and nested expansion](https://github.com/moikdev/droidcast/issues/9)
- [#10: Add rendered Markdown and portable note workflows](https://github.com/moikdev/droidcast/issues/10)
- [#11: Add a searchable offline emoji and symbol picker](https://github.com/moikdev/droidcast/issues/11)
- [#12: Add dictionary lookup and definition views](https://github.com/moikdev/droidcast/issues/12)
- [#13: Extend calculator parity with dates, colors and exchange rates](https://github.com/moikdev/droidcast/issues/13)
- [#14: Expand explicit clipboard collections with images and useful actions](https://github.com/moikdev/droidcast/issues/14)
- [#15: Implement tested Android multitasking equivalents for Tinycast window actions](https://github.com/moikdev/droidcast/issues/15)
- [#16: Improve application focus, running-state and launch behavior](https://github.com/moikdev/droidcast/issues/16)
- [#17: Add calendar search, upcoming meetings and join actions](https://github.com/moikdev/droidcast/issues/17)
- [#18: Add optional provider-backed chat with saved conversations](https://github.com/moikdev/droidcast/issues/18)
- [#19: Add selected-text rewrite, translation and summary actions](https://github.com/moikdev/droidcast/issues/19)
- [#20: Complete theme, localization and accessibility parity](https://github.com/moikdev/droidcast/issues/20)
- [#21: Enable clean-runner CI and production release engineering](https://github.com/moikdev/droidcast/issues/21)

- [#22: Validate battery and security destinations across Android builds](https://github.com/moikdev/droidcast/issues/22)

- [#24: Import supported Tinycast and Raycast export categories on Android](https://github.com/moikdev/droidcast/issues/24)
- [#26: Add modifier-tap hotkeys and verified Android Hyper/Fn equivalents](https://github.com/moikdev/droidcast/issues/26)

- [#36: Compact Raycast-style settings workspace](https://github.com/moikdev/droidcast/issues/36)
- [#37: Diagnose intermittent Quick Settings tile activation timeout](https://github.com/moikdev/droidcast/issues/37)

## Active slice

Rigid typing/keyboard layout [#46](https://github.com/moikdev/droidcast/issues/46): keep command and emoji search origins steady when the IME opens, avoid repositioning already visible selections, reset query results in the next layout and preserve selected commands during caret edits. See [validation](validation/rigid-palette.md) for negative controls and bounded local evidence. Audit/merge state is recorded on the implementation PR and live issue #1.

Queued user requests: inline keyboard readiness [#39](https://github.com/moikdev/droidcast/issues/39), explicit corner-swipe/Home-hold assistant setup and actual gesture verification [#47](https://github.com/moikdev/droidcast/issues/47), and double-tap Ctrl [#26](https://github.com/moikdev/droidcast/issues/26). Readiness WIP is preserved on its own branch; these are not claimed as part of the movement fix.

## Completed slices

The direct-settings slice merged in [PR #23](https://github.com/moikdev/droidcast/pull/23), closing #2 after both independent audits cleared head `39fc5f8` and local checks passed. Its live review/merge state and [issue #1](https://github.com/moikdev/droidcast/issues/1) are authoritative. Record subsequent slices here after their verified merges; the baseline preview is not full parity.

Backup/restore merged in [PR #25](https://github.com/moikdev/droidcast/pull/25), closing #3 after both independent audits cleared `d144ae0`: 31 JVM, 39 tablet and 10 phone tests, build/lint, and 18 inspected screenshots. CI remains inactive.

Configurable ordinary keyboard shortcuts merged in [PR #33](https://github.com/moikdev/droidcast/pull/33), closing #4 and #34 after both sol/high audits cleared `8ccd87d`: 46 JVM, 52 tablet, 23 phone and 18 Linux hardware cases, plus build/lint and 21 screenshots. The unexplained phone backup timeout remains tracked in #35.

Raycast-style settings, scoped folders, assistant activation and immediate search merged in [PR #38](https://github.com/moikdev/droidcast/pull/38), closing #36/#40 after both audits cleared `1508ac6`. Final scope: 66 JVM, 27 targeted tests per layout, 16+5 hardware cases and 39 screenshots. #5 remains partial; the 71-test full suites belong to an earlier binary.

Fullscreen tablet Settings merged in [PR #43](https://github.com/moikdev/droidcast/pull/43), closing #42 after both audits cleared `0ac6689`: 66 JVM tests, 16 targeted tests per layout and eight screenshots. The palette stays floating. [Nightly r2](https://github.com/moikdev/droidcast/releases/tag/nightly-2026-10-31-r2) is the released checkpoint for that source.

## Additional upstream gaps

- [#27: Editable settings mirror and history](https://github.com/moikdev/droidcast/issues/27)
- [#28: Local dictation](https://github.com/moikdev/droidcast/issues/28)
- [#29: Camera preview and capture](https://github.com/moikdev/droidcast/issues/29)
- [#30: MCP](https://github.com/moikdev/droidcast/issues/30)
- [#31: Frontmost app menu/action search](https://github.com/moikdev/droidcast/issues/31)
- [#32: Custom command library and scoped automation](https://github.com/moikdev/droidcast/issues/32)

- [#34: Predictable root Settings routing from warm search](https://github.com/moikdev/droidcast/issues/34), exposed and addressed within PR #33.

Global-shortcut onboarding follow-up: [#39](https://github.com/moikdev/droidcast/issues/39). A Lenovo user reports Alt+Q working after approving restricted settings; broader OEM certification remains open.

Assistant activation and immediate search: [#40](https://github.com/moikdev/droidcast/issues/40), merged in PR #38. The custom global hotkey remains separate from the system assistant mapping.

Validation limitation [#41](https://github.com/moikdev/droidcast/issues/41): HTML Viewer rendered the correct TXT body but omitted it from accessibility XML in one hardware run; an unchanged rerun passed. Cause and fix remain unestablished.

Tablet Settings fullscreen polish: [#42](https://github.com/moikdev/droidcast/issues/42), merged in PR #43; the palette remains floating.

Offline Emoji & Symbols merged in [PR #45](https://github.com/moikdev/droidcast/pull/45), closing #11 after both audits cleared `679c69e`; merge `b974ff0`. Final evidence: 71 JVM, 11 native tests per layout and 25 screenshots, build/lint, exact Unicode copying and persistence. [Nightly r3](https://github.com/moikdev/droidcast/releases/tag/nightly-2026-10-31-r3) is the published checkpoint. Advanced emoji parity remains in [#44](https://github.com/moikdev/droidcast/issues/44).
