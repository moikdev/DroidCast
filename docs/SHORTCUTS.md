# Keyboard shortcuts

Development builds add configurable global and command bindings. The tagged 0.4.0-preview predates this feature.

Search **Keyboard shortcuts** to change the default **Ctrl + Space** summon/toggle chord. Select any stable command, app or saved item, open **Actions → Set keyboard shortcut**, press a chord, then save. Result rows display assigned keycaps. The manager can change or clear bindings, including bindings whose commands are no longer available. Reset all requires confirmation and restores the global default without changing collections or Android consent.

The recorder accepts Ctrl, Alt or Meta with a supported key, or F1–F12. Shift can be added. Modifier sides and lock state are normalized; labels use the connected keyboard's display character where available. Plain typing, Escape, internal palette combinations and known Android navigation conflicts cannot be assigned. Tab and Shift+Tab navigate the recorder controls without changing the candidate; Enter activates the focused control. Duplicate chords name their existing command and cannot be saved. Device-specific OS shortcuts may still intercept otherwise valid combinations.

Global activation requires the optional **DroidCast keyboard shortcuts** accessibility service, enabled by the user in Android settings. Without it, configured bindings work in the main palette. Global dispatch uses the same command execution paths as selection and Enter: app and Settings destinations dismiss the palette; a note opens its editor; calculator enters calculator mode. The global chord toggles the palette and the calculator binding toggles its existing mode. Repeats do not execute commands repeatedly. Recording pauses global activation and leaving the app cancels recording.

The service requests key filtering, not screen content. It stores assigned bindings, not a history of typed keys. It consumes matched key-down/up pairs and lets unrelated keys pass through. An expiring, single-use in-process token carries each service invocation; external intents cannot supply an arbitrary command target. Reassigned, expired and replayed invocations are rejected. Import never enables the Android service.

Bindings can be included in [backup and restore](BACKUP.md). Bindings to recent documents remain local because their URI grants cannot be transferred. Conflicts with those preserved bindings block import. Imported commands unavailable on this device remain editable in the manager and produce readable feedback when invoked.

Modifier-only/double-tap, sided modifiers, Hyper, Fn/Globe, app-specific key interception and universal OEM delivery are not certified. These advanced upstream cases remain tracked in [#26](https://github.com/moikdev/DroidCast/issues/26). TalkBack, every keyboard layout and physical-device coverage also remain open. See [validation](validation/shortcuts.md) for the tested device and cases.
