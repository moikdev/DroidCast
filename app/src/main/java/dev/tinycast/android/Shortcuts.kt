package dev.tinycast.android

import android.view.KeyEvent
import java.util.UUID

enum class ShortcutTrigger { CHORD, DOUBLE_TAP }
enum class ModifierSide { ANY, LEFT, RIGHT }

/** One persisted shortcut identity, shared by the recorder, dispatcher and backups. */
data class KeyChord(val keyCode: Int, val modifiers: Int,
    val trigger: ShortcutTrigger = ShortcutTrigger.CHORD, val side: ModifierSide = ModifierSide.ANY)
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
    fun modifierFamily(key: Int): Int? = when (key) {
        KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> CTRL
        KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> ALT
        KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> SHIFT
        KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT -> META
        else -> null
    }
    fun modifierCode(family: Int): Int = when (family) {
        CTRL -> KeyEvent.KEYCODE_CTRL_LEFT
        ALT -> KeyEvent.KEYCODE_ALT_LEFT
        SHIFT -> KeyEvent.KEYCODE_SHIFT_LEFT
        META -> KeyEvent.KEYCODE_META_LEFT
        else -> error("Unsupported modifier")
    }
    fun modifierSide(key: Int): ModifierSide = when (key) {
        KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_META_LEFT -> ModifierSide.LEFT
        KeyEvent.KEYCODE_CTRL_RIGHT, KeyEvent.KEYCODE_ALT_RIGHT, KeyEvent.KEYCODE_SHIFT_RIGHT, KeyEvent.KEYCODE_META_RIGHT -> ModifierSide.RIGHT
        else -> error("Unsupported modifier")
    }
    fun doubleTap(family: Int, side: ModifierSide = ModifierSide.ANY) = KeyChord(modifierCode(family), 0, ShortcutTrigger.DOUBLE_TAP, side)
    fun overlaps(a: KeyChord, b: KeyChord): Boolean = a == b ||
        (a.trigger == ShortcutTrigger.DOUBLE_TAP && b.trigger == a.trigger && a.keyCode == b.keyCode &&
            (a.side == ModifierSide.ANY || b.side == ModifierSide.ANY))
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
        if (chord.trigger == ShortcutTrigger.DOUBLE_TAP) {
            val family = modifierFamily(key)
            return if (modifiers != 0 || family == null || modifierCode(family) != key)
                "Double-tap shortcuts use Ctrl, Alt, Shift or Meta without another key" else null
        }
        if (chord.side != ModifierSide.ANY) return "Side selection is available for double-tap modifiers"
        val function = key in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12
        if (modifiers !in 0..15) return "Unsupported modifier combination"
        if (key == KeyEvent.KEYCODE_ESCAPE) return "Escape is reserved for cancelling and dismissing"
        if (key !in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z && key !in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 &&
            key !in KeyEvent.KEYCODE_COMMA..KeyEvent.KEYCODE_PERIOD && key !in KeyEvent.KEYCODE_GRAVE..KeyEvent.KEYCODE_SLASH && key !in navigation && !function)
            return "Choose a letter, number, navigation key or F1–F12"
        if (modifiers and (CTRL or ALT or META) == 0 && !function) return "Use Ctrl, Alt or Meta; plain typing is never a global shortcut"
        if (modifiers in setOf(CTRL, META) && key in editing) return "Reserved for Droidcast's search, editor or navigation controls"
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
        configuration.bindings.values.filter { it.trigger == ShortcutTrigger.DOUBLE_TAP }.groupBy { it.keyCode }.values.forEach { taps ->
            require(taps.size <= 1 || taps.none { it.side == ModifierSide.ANY }) { "Either-side modifier shortcut conflicts with a left or right binding" }
        }
    }
}

/** No text is stored: only the device/key pairs whose down events were consumed. */
class ShortcutEngine {
    data class Input(val down: Boolean, val keyCode: Int, val modifiers: Int, val repeat: Int = 0, val device: Int = 0,
        val eventTime: Long = 0, val downTime: Long = eventTime, val receivedAt: Long = eventTime, val canceled: Boolean = false)
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

/** Observes modifier pairs without consuming them, preserving ordinary Ctrl+C etc. */
class ModifierTapDetector {
    companion object {
        const val MAX_HOLD_MS = 250L
        const val MAX_GAP_MS = 350L
        const val MAX_DELIVERY_AGE_MS = 1000L
    }
    private data class Tap(val device: Int, val key: Int, val releasedAt: Long, val startedAt: Long)
    private data class Press(val device: Int, val key: Int, val downTime: Long, val second: Boolean, val startedAt: Long)
    private var first: Tap? = null
    private var press: Press? = null
    private var lastTime: Long? = null
    val sequenceStartedAt: Long? get() = press?.startedAt ?: first?.startedAt
    fun handle(input: ShortcutEngine.Input): KeyChord? {
        val time = input.eventTime
        if (input.canceled || time < 0 || input.downTime < 0 || input.receivedAt < 0 || time < input.downTime ||
            input.receivedAt - time !in 0..MAX_DELIVERY_AGE_MS || lastTime?.let { time < it } == true) {
            clear(); return null
        }
        lastTime = time
        val family = ShortcutRules.modifierFamily(input.keyCode)
        if (family == null || input.modifiers and family.inv() != 0 || input.repeat != 0) {
            first = null; press = null; return null
        }
        if (input.down) {
            if (press != null || time != input.downTime) { first = null; press = null; return null }
            val previous = first
            val second = previous != null && previous.device == input.device && previous.key == input.keyCode &&
                time - previous.releasedAt in 0..MAX_GAP_MS
            first = null
            press = Press(input.device, input.keyCode, input.downTime, second, if (second) previous!!.startedAt else input.downTime)
            return null
        }
        val held = press
        press = null
        if (held == null || held.device != input.device || held.key != input.keyCode || held.downTime != input.downTime ||
            time - held.downTime !in 0..MAX_HOLD_MS) { first = null; return null }
        if (!held.second) { first = Tap(input.device, input.keyCode, time, held.startedAt); return null }
        first = null
        return ShortcutRules.doubleTap(family, ShortcutRules.modifierSide(input.keyCode))
    }
    fun clear() { first = null; press = null; lastTime = null }
}

fun Map<KeyChord, String>.targetForTap(tap: KeyChord): Pair<String, KeyChord>? {
    val binding = if (containsKey(tap)) tap else tap.copy(side = ModifierSide.ANY)
    return get(binding)?.let { it to binding }
}

/** A connection or binding change cannot finish an earlier owner's partial tap. */
class ModifierShortcutObserver {
    private val detector = ModifierTapDetector()
    private var previousBindings: Map<KeyChord, String>? = null
    private var previousEpoch: Long? = null
    private var previousRecordingEpoch: Long? = null
    fun handle(input: ShortcutEngine.Input, bindings: Map<KeyChord, String>, enabled: Boolean = true, epoch: Long = 0, recordingEpoch: Long = 0): Pair<String, KeyChord>? {
        if (!enabled || previousBindings != bindings || previousEpoch != epoch || previousRecordingEpoch != recordingEpoch) detector.clear()
        previousBindings = bindings; previousEpoch = epoch; previousRecordingEpoch = recordingEpoch
        if (!enabled) return null
        return detector.handle(input)?.let(bindings::targetForTap)
    }
    fun clear() { detector.clear(); previousBindings = null; previousEpoch = null; previousRecordingEpoch = null }
    fun windowChanged(windowClass: CharSequence?, eventTime: Long? = null) {
        // IME visibility does not switch the app receiving keyboard input.
        if (windowClass?.toString() == "android.inputmethodservice.SoftInputWindow") return
        // Accessibility notifications may arrive after newer key events. A window
        // change sent before this sequence cannot cancel keys typed in the new window.
        val started = detector.sequenceStartedAt
        if (eventTime == null || eventTime <= 0 || started == null || eventTime >= started) clear()
    }
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
