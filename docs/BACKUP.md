# Backup and restore

Search **Backup and restore**, **Export DroidCast backup** or **Import DroidCast backup**. The same entry is in Preferences. Available in development builds after the original 0.4.0-preview release.

Select notes, quicklinks, saved clips, pins/aliases usage ranking and keyboard bindings independently. Export opens Android's document picker. The output is readable JSON: notes and saved clips may contain private text, so choose its destination accordingly. Cancelling a picker does not change your collections.

Import first reads and validates the selected file. Select available categories, inspect their contents, choose a policy, review the confirmation, then apply. Previewing, cancelling and invalid input do not change stored data. Import does not open imported links or execute commands.

- **Merge** keeps existing versions when identities conflict and skips identical content. Identity matches take priority over content duplicates, preserving edited items and their references. Identical content with a different ID is mapped to its existing identity for imported pins, aliases and ranking. Existing alias overrides and ranking entries take precedence. When duplicate incoming identities map to one item, conflicting aliases block the merge and ranking uses the higher count. Pins are combined.
- **Replace selected categories** replaces only categories you select. Unselected collections and recent documents remain. The confirmation explains that current selected data will be removed.
- Effective alias collisions, including aliases on preserved recent documents, block the entire plan; nothing is partially applied. Exclude the conflicting category or change the alias first. Document aliases are checked locally but remain excluded from portable backups.
- Merging saved clips past their 100-item limit is rejected rather than silently discarding data.

The final apply uses one preferences transaction on an IO dispatcher, then publishes the resulting state. The UI prevents a second operation while it saves. A successful import is available to the next store instance without requiring an app restart.

## Format and limits

DroidCast format identifier `dev.droidcast.backup`, version **1**, accepts only the current version. Root fields are exactly `format`, `version`, `categories`. Categories are `NOTES`, `LINKS`, `CLIPS`, `PREFERENCES`, `LEARNING` and `SHORTCUTS`; unchecked categories are absent. Items carry `id`, `title`, `body`, `alias`. Preferences carry `pins` and `aliases`; learning is a command-ID-to-count map. Shortcuts contain `version: 1` and a `bindings` map from command ID to `keyCode`/`modifiers`. Up to 2,048 bindings are accepted using the same rules as the recorder.

The reader bounds input to **5 MiB**, validates UTF-8, rejects duplicate/unknown fields, trailing data, excessive nesting, invalid identities and unsupported values. Limits: 2,000 notes or quicklinks per category, 100 saved clips, 5,000 pins/aliases/ranking entries, 512-character titles and 131,072-character bodies. Links use the same HTTP/HTTPS host validation as the Android editor, preserving accepted internationalized URLs and raw values. Aliases follow the palette's existing syntax. Export validates against the same schema and limits.

## Coverage and exclusions

| Palette preference | Treatment |
| --- | --- |
| `notes`, `links`, `clips` | Selected content categories |
| `pins`, `aliases` | Selected command preferences; document references excluded |
| `usage` | Selected launcher learning; document references excluded |
| `shortcuts` | Selected keyboard bindings; recent-document bindings excluded and preserved locally |
| `files` | Excluded: recent-document URIs and their grants are device-specific |
| Extension preferences, credentials, code and storage | Separate store, never read by backup |
| Accessibility consent and other Android permissions | Android-owned state, never read or written by import |

This is an Android-native format. It does **not** read Tinycast's AppleArchive/LZFSE `.tinycast` files or Raycast export containers. That compatibility remains tracked in [#24](https://github.com/moikdev/DroidCast/issues/24). A JSON backup is not encrypted. It contains no filesystem archive entries to extract and cannot grant file access or enable a service.

## Keyboard binding compatibility

The optional `SHORTCUTS` category is additive within version 1. Older backups without it preserve current bindings. Pre-shortcut DroidCast readers reject the unknown category; uncheck keyboard shortcuts when exporting for those versions. Merge keeps current bindings for existing targets and remaps bindings when identical imported content maps to a local item. Replacement changes selected portable bindings while retaining local document bindings. Conflicting chords, including preserved document bindings, block the plan. The confirmation warns that imported bindings become active if the user already enabled the service. Consent itself is never imported.
