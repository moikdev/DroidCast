# Settings

Available in development builds after the original 0.4.0-preview tag. Search **Droidcast preferences** (`prefs`) or press **Ctrl/Meta+,** while the palette is open. This opens a separate native settings workspace. Tablet Settings fills the safe screen area, with a persistent sidebar; phone uses compact page navigation and a command detail page. The command palette remains a separate compact floating overlay.

- **General:** Android assistant selection, custom global shortcut, optional Android keyboard service and data information. The assistant route avoids keyboard-service approval; see [Opening Droidcast](ACTIVATION.md).
- **Shortcuts:** search command titles, aliases and chords; filter to assigned entries. Select a command to edit its alias or record, clear or reset shortcuts inline. Conflicts identify their owner and disable Save. Clearing a candidate with Escape keeps the saved binding. Tab/Shift+Tab move focus; Enter, keypad Enter and D-pad Center activate the focused control.
- **Folders:** choose scopes, refresh metadata, reauthorize or remove access. See [Folder search](FOLDER-SEARCH.md).
- **Backup:** export categories or preview an import; review and confirmation stay in the same pane. See [Backup](BACKUP.md).
- **About:** version, source and license information.

**Ctrl/Meta+F** focuses settings search, which can jump to a page or a command. **Ctrl/Meta+Shift+,** in the palette, or **Actions → Configure command**, opens the selected command’s settings. Actions → Set alias focuses the alias editor; Set keyboard shortcut opens the inline recorder. These navigation keys take precedence inside Droidcast, preserving existing global assignments outside the app.

Back/Escape cancels the current recorder or backup review before closing the workspace. Open palette returns to a fresh preferences snapshot. Leaving to an external app cancels an unsaved recorded chord. Android service consent and document/folder pickers remain Android-owned surfaces. Appearance, full keyboard remapping and other unsupported Raycast settings are not presented as working options.

The layout and entry points follow [Raycast Settings](https://manual.raycast.com/settings); this is an unofficial Android adaptation with partial parity.
