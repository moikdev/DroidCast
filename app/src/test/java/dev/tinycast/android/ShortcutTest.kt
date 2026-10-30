package dev.tinycast.android

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class ShortcutTest {
    private val chord = KeyChord(KeyEvent.KEYCODE_W, ShortcutRules.CTRL or ShortcutRules.ALT)
    private val bindings = mapOf(chord to "system:wifi")
    private fun down(key: Int = chord.keyCode, modifiers: Int = chord.modifiers, repeat: Int = 0, device: Int = 1) = ShortcutEngine.Input(true, key, modifiers, repeat, device)
    private fun up(key: Int = chord.keyCode, modifiers: Int = 0, device: Int = 1) = ShortcutEngine.Input(false, key, modifiers, device = device)
    @Test fun defaultAndDisabledConfigurationsAreValid() {
        ShortcutRules.validate(ShortcutConfiguration())
        ShortcutRules.validate(ShortcutConfiguration(emptyMap()))
        assertEquals(KeyChord(KeyEvent.KEYCODE_SPACE, ShortcutRules.CTRL), ShortcutConfiguration().bindings[ShortcutRules.PALETTE])
    }
    @Test fun plainTypingAndSystemOrEditorConflictsAreRejected() {
        listOf(KeyChord(KeyEvent.KEYCODE_A, 0), KeyChord(KeyEvent.KEYCODE_A, ShortcutRules.SHIFT),
            KeyChord(KeyEvent.KEYCODE_N, ShortcutRules.CTRL), KeyChord(KeyEvent.KEYCODE_TAB, ShortcutRules.ALT),
            KeyChord(KeyEvent.KEYCODE_L, ShortcutRules.META), KeyChord(KeyEvent.KEYCODE_FORWARD_DEL, ShortcutRules.CTRL or ShortcutRules.ALT),
            KeyChord(KeyEvent.KEYCODE_VOLUME_UP, ShortcutRules.CTRL), KeyChord(KeyEvent.KEYCODE_W, 16)).forEach { assertNotNull(it.toString(), ShortcutRules.error(it)) }
        assertNull(ShortcutRules.error(chord))
        assertNull(ShortcutRules.error(KeyChord(KeyEvent.KEYCODE_F6, 0)))
        assertNull(ShortcutRules.error(KeyChord(KeyEvent.KEYCODE_DPAD_LEFT, ShortcutRules.CTRL or ShortcutRules.ALT)))
    }
    @Test fun duplicateBindingsAndEphemeralTargetsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { ShortcutRules.validate(ShortcutConfiguration(mapOf("system:wifi" to chord, "calculator" to chord))) }
        assertThrows(IllegalArgumentException::class.java) { ShortcutRules.validate(ShortcutConfiguration(mapOf("web" to chord))) }
        ShortcutRules.validate(ShortcutConfiguration(mapOf("app:dev.example/dev.example.Main\$Nested" to chord)))
    }
    @Test fun matchingDownAndUpAreConsumedEvenAfterModifiersAreReleased() {
        val engine = ShortcutEngine()
        assertEquals(ShortcutEngine.Result(true, "system:wifi", chord), engine.handle(down(), bindings))
        assertEquals(ShortcutEngine.Result(true), engine.handle(up(), bindings))
        assertFalse(engine.handle(up(), bindings).consumed)
    }
    @Test fun repeatsNeverDispatchAnotherCommandOrCaptureAnUnmatchedHold() {
        val engine = ShortcutEngine()
        engine.handle(down(), bindings)
        repeat(20) { assertEquals(ShortcutEngine.Result(true), engine.handle(down(repeat = it + 1), bindings)) }
        engine.handle(up(), bindings)
        assertEquals(ShortcutEngine.Result(false), engine.handle(down(repeat = 1), bindings))
    }
    @Test fun unrelatedTypingAndUnboundChordsPassBothEnds() {
        val engine = ShortcutEngine()
        assertFalse(engine.handle(down(modifiers = 0), bindings).consumed)
        assertFalse(engine.handle(up(), bindings).consumed)
        assertFalse(engine.handle(down(key = KeyEvent.KEYCODE_Z), bindings).consumed)
        assertFalse(engine.handle(up(key = KeyEvent.KEYCODE_Z), bindings).consumed)
    }
    @Test fun recordingPauseAndRebindingPreserveAlreadyConsumedReleases() {
        val engine = ShortcutEngine()
        engine.handle(down(), bindings)
        assertEquals(ShortcutEngine.Result(true), engine.handle(up(), emptyMap(), paused = true))
        assertFalse(engine.handle(down(), bindings, paused = true).consumed)
        assertFalse(engine.handle(up(), bindings, paused = true).consumed)
        assertEquals("system:wifi", engine.handle(down(), bindings).target)
    }
    @Test fun freshPressRecoversFromLostReleaseWithoutSwallowingTyping() {
        val engine = ShortcutEngine()
        engine.handle(down(), bindings)
        assertFalse(engine.handle(down(modifiers = 0), bindings).consumed)
        assertFalse(engine.handle(up(), bindings).consumed)
        assertEquals("system:wifi", engine.handle(down(), bindings).target)
    }
    @Test fun devicesAreTrackedSeparatelyAndDisconnectedDevicesAreCleared() {
        val engine = ShortcutEngine()
        engine.handle(down(device = 1), bindings)
        assertFalse(engine.handle(up(device = 2), bindings).consumed)
        assertTrue(engine.handle(up(device = 1), bindings).consumed)
        engine.handle(down(device = 2), bindings)
        engine.clearDevice(2)
        assertFalse(engine.handle(up(device = 2), bindings).consumed)
        assertEquals("system:wifi", engine.handle(down(device = 2), bindings).target)
        engine.clear()
        assertFalse(engine.handle(up(device = 2), bindings).consumed)
    }
    @Test fun invocationTokensAreOneUseAndExpireBeforeAndAfterConsumption() {
        var clock = 100L
        val queue = ShortcutInvocationQueue { clock }
        assertNull(queue.consume("forged")); assertNull(queue.consume(null))
        val token = queue.issue("system:wifi", chord)
        val invocation = queue.consume(token)!!
        assertEquals("system:wifi", invocation.target); assertEquals(chord, invocation.chord)
        assertNull(queue.consume(token))
        clock += 10_001
        assertFalse(queue.isFresh(invocation))
        val expired = queue.issue("calculator", chord)
        clock += 10_001
        assertNull(queue.consume(expired))
    }
    @Test fun outstandingInvocationTokensAreBounded() {
        val queue = ShortcutInvocationQueue { 10L }
        val first = queue.issue("calculator", chord)
        val tokens = (1..32).map { queue.issue("system:wifi", chord) }
        assertNull(queue.consume(first))
        assertEquals("system:wifi", queue.consume(tokens.last())!!.target)
    }
}
