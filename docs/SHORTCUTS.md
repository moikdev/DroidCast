# Keyboard shortcuts

Current nightly builds include configurable global and command bindings. The in-app version remains 0.4.0-preview; the release tag identifies the checkpoint.

Search **Keyboard shortcuts** to change the default **Ctrl + Space** summon/toggle chord. Select any stable command, app or saved item, open **Actions → Set keyboard shortcut**, press a chord, then save. Result rows display assigned keycaps. The dedicated Settings → Shortcuts workspace can change or clear bindings, including bindings whose commands are no longer available. It also edits available commands’ aliases inline. Search commands or use the Assigned filter. Reset all requires confirmation and restores the global default without changing collections or Android consent.

The recorder accepts Ctrl, Alt or Meta with a supported key, or F1–F12. Shift can be added. Modifier sides and lock state are normalized; labels use the connected keyboard's display character where available. Plain typing, Escape, internal palette combinations and known Android navigation conflicts cannot be assigned. Tab and Shift+Tab navigate the recorder controls without changing the candidate; Enter or keypad Enter activates the focused control. Directional and paging keys keep their normal navigation behavior. Duplicate chords name their existing command and cannot be saved. Device-specific OS shortcuts may still intercept otherwise valid combinations.

Global activation requires the optional **Droidcast keyboard shortcuts** accessibility service, enabled by the user in Android settings. Without it, configured bindings work in the main palette. Global dispatch uses the same command execution paths as selection and Enter: app and Settings destinations dismiss the palette; a note opens its editor; calculator enters calculator mode. The global chord toggles the palette and the calculator binding toggles its existing mode. Repeats do not execute commands repeatedly. Recording pauses global activation and leaving the app cancels recording.

The service requests key filtering, not screen content. It stores assigned bindings, not a history of typed keys. It consumes matched key-down/up pairs and lets unrelated keys pass through. An expiring, single-use in-process token carries each service invocation; external intents cannot supply an arbitrary command target. Reassigned, expired and replayed invocations are rejected. Import never enables the Android service.

Bindings can be included in [backup and restore](BACKUP.md). Bindings to recent and indexed documents remain local because their URI grants cannot be transferred. Conflicts with those preserved bindings block import. Imported commands unavailable on this device remain editable in the manager and produce readable feedback when invoked.

Modifier-only/double-tap, sided modifiers, Hyper, Fn/Globe, app-specific key interception and universal OEM delivery are not certified. These advanced upstream cases remain tracked in [#26](https://github.com/moikdev/droidcast/issues/26). TalkBack, every keyboard layout and physical-device coverage also remain open. Settings navigation uses Ctrl/Meta+, from the palette, Ctrl/Meta+Shift+, for the selected command and Ctrl/Meta+F within Settings. These navigation keys take precedence inside Droidcast; existing global assignments still work in other apps. See [Settings](SETTINGS.md) and [validation](validation/shortcuts.md) for the tested device and cases.

## Enabling global activation for a downloaded APK

Settings → General separates the saved global shortcut, Android approval and live service connection. Saving a chord does not grant Android approval, and approval alone does not establish that the service is connected.

Open Droidcast Settings → General → Enable service, then select **Droidcast keyboard shortcuts** under Android Accessibility's Installed/Downloaded apps and enable it. The published 2026-10-30 checkpoint still uses the older “DroidCast” label.

If Android says access was denied or the setting is restricted, open **Android Settings → Apps → Droidcast → ⋮ → Allow restricted settings**, then return to Accessibility. Only approve this for an APK whose source you trust; see [Google's instructions](https://support.google.com/android/answer/12623953?hl=en). This is user-controlled Android consent, not an app permission Droidcast can grant itself. Without it, use the app icon or Quick Settings tile. Lenovo/OEM labels may differ.


## Check shortcut delivery

With a global chord saved, Android approval enabled and the service connected, Settings → General offers **Check shortcut** inline. Press the configured chord within 15 seconds. The service reports only a matching palette-shortcut identity; the check does not capture typing or unrelated key events. A received result means delivery was observed in this Settings screen. Switch to another app and try the shortcut there to verify global activation on your keyboard.

The check cancels when Settings loses focus, backgrounds, changes pages, enters Settings search, or the chord/approval/connection changes. Each check is bound to a service-connection epoch, so a rapid reconnect cannot preserve it. While explicitly armed, its configured global chord can take precedence over Settings navigation such as Ctrl+F; normal navigation resumes after receipt, cancellation or expiry. A timeout says no shortcut was received; it does not claim that the keyboard or Android configuration is working. This diagnostic never enables consent. App info and Google's setup instructions remain available beside the restricted-settings explanation, and the app icon, Open palette button, Quick Settings tile and optional system assistant remain alternatives.

Component names in Android's approval list are parsed as full or shortened `ComponentName` values, with the master accessibility switch checked separately. A service instance can only clear its own live connection, so a late old-instance disconnect cannot erase a replacement binding. Lenovo/ZUXOS delivery remains user-reported; emulator evidence is not a physical-device certification.
