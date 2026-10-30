package dev.tinycast.android

import android.view.KeyEvent
import java.util.UUID

data class KeyChord(val keyCode: Int, val modifiers: Int)
data class ShortcutConfiguration(val bindings: Map<String, KeyChord> = mapOf(ShortcutRules.PALETTE to KeyChord(KeyEvent.KEYCODE_SPACE, ShortcutRules.CTRL))) {
    val byChord: Map<KeyChord, String> = bindings.entries.associate { it.value to it.key }
}

object ShortcutRules {
    const val PALETTE = "global:palette"
    const val CTRL = 1
    const val ALT = 2
    const val SHIFT = 4
    const val META = 8
    const val MAX_BINDINGS = 2048
    private val reference = Regex("[A-Za-z0-9._:/$-]{1,1024}")
    private val navigation = setOf(KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL, KeyEvent.KEYCODE_MOVE_HOME, KeyEvent.KEYCODE_MOVE_END,
        KeyEvent.KEYCODE_PAGE_UP, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT)
    private val editing = setOf(KeyEvent.KEYCODE_K, KeyEvent.KEYCODE_L, KeyEvent.KEYCODE_N, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_S,
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER) + (KeyEvent.KEYCODE_1..KeyEvent.KEYCODE_8)
    fun targetAllowed(id: String) = reference.matches(id) && id !in setOf("result", "web", "backup-write", "backup-read")
    fun error(chord: KeyChord): String? {
        val key = chord.keyCode; val modifiers = chord.modifiers
        val function = key in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12
        if (modifiers !in 0..15) return "Unsupported modifier combination"
        if (key == KeyEvent.KEYCODE_ESCAPE) return "Escape is reserved for cancelling and dismissing"
        if (key !in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z && key !in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 &&
            key !in KeyEvent.KEYCODE_COMMA..KeyEvent.KEYCODE_PERIOD && key !in KeyEvent.KEYCODE_GRAVE..KeyEvent.KEYCODE_SLASH && key !in navigation && !function)
            return "Choose a letter, number, navigation key or F1–F12"
        if (modifiers and (CTRL or ALT or META) == 0 && !function) return "Use Ctrl, Alt or Meta; plain typing is never a global shortcut"
        if (modifiers in setOf(CTRL, META) && key in editing) return "Reserved for DroidCast's search, editor or navigation controls"
        if (modifiers in setOf(ALT, ALT or SHIFT) && key in setOf(KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_F4)) return "Reserved for Android window navigation"
        if (modifiers in setOf(META, META or SHIFT) && key in setOf(KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_H, KeyEvent.KEYCODE_L, KeyEvent.KEYCODE_SLASH)) return "Reserved for Android system navigation"
        if (modifiers and (CTRL or ALT) == (CTRL or ALT) && key in setOf(KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL)) return "Reserved for system control"
        return null
    }
    fun validate(configuration: ShortcutConfiguration) {
        require(configuration.bindings.size <= MAX_BINDINGS) { "Too many keyboard shortcuts" }
        configuration.bindings.forEach { (target, chord) ->
            require(targetAllowed(target)) { "Invalid shortcut target" }
            require(error(chord) == null) { error(chord)!! }
        }
        require(configuration.byChord.size == configuration.bindings.size) { "Two commands use the same shortcut" }
    }
}

/** No text is stored: only the device/key pairs whose down events were consumed. */
class ShortcutEngine {
    data class Input(val down: Boolean, val keyCode: Int, val modifiers: Int, val repeat: Int = 0, val device: Int = 0)
    data class Result(val consumed: Boolean, val target: String? = null, val chord: KeyChord? = null)
    private val held = mutableSetOf<Pair<Int, Int>>()
    fun handle(input: Input, bindings: Map<KeyChord, String>, paused: Boolean = false): Result {
        val key = input.device to input.keyCode
        if (!input.down) return Result(held.remove(key))
        if (input.repeat > 0) return Result(key in held)
        // A fresh press recovers from a missing release without swallowing new unrelated typing.
        held.remove(key)
        val chord = KeyChord(input.keyCode, input.modifiers)
        val target = if (paused) null else bindings[chord]
        if (target == null) return Result(false)
        held.add(key)
        return Result(true, target, chord)
    }
    fun clear() = held.clear()
    fun clearDevice(device: Int) { held.removeAll { it.first == device } }
}

data class ShortcutInvocation(val target: String, val chord: KeyChord, val expiresAt: Long)

/** The exported palette never trusts a command ID supplied by another app. */
class ShortcutInvocationQueue(private val now: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private val requests = linkedMapOf<String, ShortcutInvocation>()
    @Synchronized fun issue(target: String, chord: KeyChord): String {
        requests.entries.removeAll { it.value.expiresAt <= now() }
        while (requests.size >= 32) requests.remove(requests.keys.first())
        val token = UUID.randomUUID().toString()
        requests[token] = ShortcutInvocation(target, chord, now() + 10_000)
        return token
    }
    @Synchronized fun consume(token: String?): ShortcutInvocation? = token?.let { requests.remove(it) }?.takeIf { it.expiresAt > now() }
    fun isFresh(invocation: ShortcutInvocation) = invocation.expiresAt > now()
}
