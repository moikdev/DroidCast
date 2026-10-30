package dev.tinycast.android

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun ShortcutDialogs(store: PaletteStore, enabled: Boolean, enable: () -> Unit, close: () -> Unit) {
    when (store.dialog) {
        "shortcut-record" -> {
            val target = store.recordingTarget ?: return
            val captured = remember(target) { mutableSetOf<Pair<Int, Int>>() }
            Shell("Record a shortcut", store.shortcutTitle(target), { store.cancelShortcutRecording() }, Modifier.onPreviewKeyEvent { composeEvent ->
                val event = composeEvent.nativeKeyEvent
                val key = event.deviceId to event.keyCode
                when {
                    event.action == KeyEvent.ACTION_UP -> captured.remove(key)
                    event.action != KeyEvent.ACTION_DOWN -> false
                    event.keyCode == KeyEvent.KEYCODE_ESCAPE && event.asChord().modifiers == 0 -> { store.cancelShortcutRecording(); true }
                    KeyEvent.isModifierKey(event.keyCode) -> false
                    event.asChord().modifiers in setOf(0, ShortcutRules.SHIFT) && event.keyCode in setOf(KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                        KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END) -> false
                    event.repeatCount > 0 -> key in captured
                    else -> { captured.add(key); store.captureShortcut(event.asChord()); true }
                }
            }) {
                Surface(border = BorderStroke(1.dp, PaletteTheme.border), modifier = Modifier.fillMaxWidth().testTag("shortcut-candidate")) {
                    Text(store.recordingCandidate?.label() ?: "Press a shortcut…", fontSize = 21.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(20.dp))
                }
                Text("Use Ctrl, Alt or Meta with a key, or F1–F12. Known Android and palette conflicts are rejected. Tab / Shift+Tab move between controls; Escape cancels.", fontSize = 12.sp, color = PaletteTheme.secondary, modifier = Modifier.padding(top = 16.dp))
                Text("Global shortcuts are paused while this recorder is open. Nothing changes until you save.", fontSize = 12.sp, color = PaletteTheme.secondary, modifier = Modifier.padding(top = 12.dp))
                store.recordingError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.padding(top = 14.dp).testTag("shortcut-record-error")) }
                Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.End) {
                    if (target in store.shortcutConfiguration.bindings) TextButton(onClick = { store.setShortcut(target, null); store.cancelShortcutRecording() }, modifier = Modifier.testTag("shortcut-clear")) { Text("Clear binding") }
                    TextButton(onClick = { store.cancelShortcutRecording() }) { Text("Cancel") }
                    Button(onClick = store::saveRecordedShortcut, enabled = store.recordingCandidate != null && store.recordingError == null, modifier = Modifier.testTag("shortcut-save")) { Text("Save shortcut") }
                }
            }
        }
        "shortcut-reset" -> Shell("Reset all shortcuts?", "Restore the original keyboard configuration.", { store.dialog = "shortcut-settings" }) {
            Text("This removes all command bindings and restores Ctrl + Space for showing or hiding DroidCast. Your notes, links, pins and Android service consent stay unchanged.", fontSize = 13.sp)
            Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { store.dialog = "shortcut-settings" }) { Text("Cancel") }
                Button(onClick = { store.resetShortcuts(); store.dialog = "shortcut-settings" }, modifier = Modifier.testTag("shortcut-reset-confirm")) { Text("Reset shortcuts") }
            }
        }
        "shortcut-settings" -> Shell("Keyboard shortcuts", "Your commands, one key combination away.", close) {
            Text("SHOW OR HIDE DROIDCAST", fontSize = 11.sp, color = PaletteTheme.secondary)
            Text(store.shortcutConfiguration.bindings[ShortcutRules.PALETTE]?.label() ?: "Not assigned", fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 8.dp).testTag("shortcut-global-value"))
            Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                Button(onClick = { store.beginShortcutRecording(ShortcutRules.PALETTE) }, modifier = Modifier.testTag("shortcut-global-record")) { Text("Change global shortcut") }
                TextButton(onClick = { runCatching { store.setShortcut(ShortcutRules.PALETTE, ShortcutConfiguration().bindings.getValue(ShortcutRules.PALETTE)) }.onFailure { store.shortcutError = it.message } }) { Text("Restore default") }
            }
            Text(if (enabled) "Keyboard service enabled. Bindings work from other apps." else "Bindings work in the main palette. Enable the optional keyboard service to use them from other apps.", fontSize = 12.sp, color = PaletteTheme.secondary, modifier = Modifier.padding(top = 14.dp))
            TextButton(onClick = enable, modifier = Modifier.testTag("shortcut-enable-service")) { Text(if (enabled) "Manage keyboard service" else "Open accessibility settings") }
            HorizontalDivider(color = PaletteTheme.separator, modifier = Modifier.padding(vertical = 14.dp))
            Text("COMMAND BINDINGS", fontSize = 11.sp, color = PaletteTheme.secondary)
            Text("Select a command, open Actions (Ctrl + K), then choose Set keyboard shortcut. Unavailable commands stay listed here so their bindings can be cleared.", fontSize = 12.sp, color = PaletteTheme.secondary, modifier = Modifier.padding(top = 8.dp))
            val titles = store.commands().associate { it.id to it.title }
            val bindings = store.shortcutConfiguration.bindings.filterKeys { it != ShortcutRules.PALETTE }.entries.sortedBy { titles[it.key] ?: it.key }
            if (bindings.isEmpty()) Text("No command shortcuts assigned", fontSize = 13.sp, color = PaletteTheme.secondary, modifier = Modifier.padding(top = 12.dp))
            else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp).testTag("shortcut-bindings")) {
                items(bindings, key = { it.key }) { (id, chord) ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(titles[id] ?: "Unavailable command · $id", fontSize = 13.sp)
                        Text(chord.label(), fontSize = 12.sp, color = PaletteTheme.secondary)
                        Row {
                            TextButton(onClick = { store.beginShortcutRecording(id) }) { Text("Change") }
                            TextButton(onClick = { store.setShortcut(id, null) }, modifier = Modifier.testTag("shortcut-remove-$id")) { Text("Clear") }
                        }
                    }
                }
            }
            store.shortcutError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp, modifier = Modifier.testTag("shortcut-settings-error")) }
            TextButton(onClick = { store.dialog = "shortcut-reset" }, modifier = Modifier.testTag("shortcut-reset")) { Text("Reset all shortcuts…") }
            TextButton(onClick = { store.dialog = "shortcut-help" }) { Text("In-palette keyboard help") }
            Text("The service filters configured key pairs only. It does not retrieve screen content or save keystrokes. Android/OEM shortcuts may affect which combinations reach the app.", fontSize = 11.sp, color = PaletteTheme.secondary, modifier = Modifier.padding(top = 10.dp))
        }
    }
}
