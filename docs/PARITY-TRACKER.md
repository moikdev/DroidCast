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

Raycast-style advanced settings [#36](https://github.com/moikdev/droidcast/issues/36) takes priority at the user’s request. It integrates selected-folder management/search from #5. Independent audits and a verified expected-head merge are required; #5 remains open for wider provider and lifecycle certification.

## Completed slices

The direct-settings slice merged in [PR #23](https://github.com/moikdev/droidcast/pull/23), closing #2 after both independent audits cleared head `39fc5f8` and local checks passed. Its live review/merge state and [issue #1](https://github.com/moikdev/droidcast/issues/1) are authoritative. Record subsequent slices here after their verified merges; the baseline preview is not full parity.

Backup/restore merged in [PR #25](https://github.com/moikdev/droidcast/pull/25), closing #3 after both independent audits cleared `d144ae0`: 31 JVM, 39 tablet and 10 phone tests, build/lint, and 18 inspected screenshots. CI remains inactive.

Configurable ordinary keyboard shortcuts merged in [PR #33](https://github.com/moikdev/droidcast/pull/33), closing #4 and #34 after both sol/high audits cleared `8ccd87d`: 46 JVM, 52 tablet, 23 phone and 18 Linux hardware cases, plus build/lint and 21 screenshots. The unexplained phone backup timeout remains tracked in #35.

## Additional upstream gaps

- [#27: Editable settings mirror and history](https://github.com/moikdev/droidcast/issues/27)
- [#28: Local dictation](https://github.com/moikdev/droidcast/issues/28)
- [#29: Camera preview and capture](https://github.com/moikdev/droidcast/issues/29)
- [#30: MCP](https://github.com/moikdev/droidcast/issues/30)
- [#31: Frontmost app menu/action search](https://github.com/moikdev/droidcast/issues/31)
- [#32: Custom command library and scoped automation](https://github.com/moikdev/droidcast/issues/32)

- [#34: Predictable root Settings routing from warm search](https://github.com/moikdev/droidcast/issues/34), exposed and addressed within PR #33.

Global-shortcut onboarding follow-up: [#39](https://github.com/moikdev/droidcast/issues/39). A Lenovo user reports Alt+Q working after approving restricted settings; broader OEM certification remains open.
