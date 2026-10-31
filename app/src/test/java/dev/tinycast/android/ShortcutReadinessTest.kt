package dev.tinycast.android

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class ShortcutReadinessTest {
    private val chord = KeyChord(KeyEvent.KEYCODE_Q, ShortcutRules.ALT)
    @Test fun aSavedChordAndApprovalAloneDoNotClaimReadiness() {
        assertFalse(ShortcutReadiness(chord, KeyboardConsent.OFF, false).ready)
        val waiting = ShortcutReadiness(chord, KeyboardConsent.APPROVED, false)
        assertFalse(waiting.ready); assertEquals("Waiting for keyboard service", waiting.title)
        assertTrue(ShortcutReadiness(chord, KeyboardConsent.APPROVED, true).ready)
    }
    @Test fun absentChordUnknownApprovalAndDisconnectStayExplicit() {
        assertEquals("Choose a global shortcut", ShortcutReadiness(null, KeyboardConsent.APPROVED, true).title)
        assertEquals("Approval status unavailable", ShortcutReadiness(chord, KeyboardConsent.UNKNOWN, true).title)
        assertFalse(ShortcutReadiness(chord, KeyboardConsent.UNKNOWN, true).ready)
        assertFalse(ShortcutReadiness(chord, KeyboardConsent.OFF, true).ready)
    }
    @Test fun lateDisconnectCannotClearANewerServiceConnection() {
        val state = KeyboardServiceConnection(); val old = Any(); val replacement = Any()
        state.connect(old); assertTrue(state.connected)
        state.connect(replacement); state.disconnect(old); assertTrue(state.connected)
        state.disconnect(replacement); assertFalse(state.connected)
    }
    @Test fun onlyTheFilteredCurrentPaletteChordCanCompleteACheckOnce() {
        val check = ShortcutProbe { 100L }
        assertFalse(check.receive(ShortcutRules.PALETTE, chord, chord, 1L))
        check.begin(chord, 1L)
        assertFalse(check.receive("calculator", chord, chord, 1L))
        assertFalse(check.receive(ShortcutRules.PALETTE, KeyChord(KeyEvent.KEYCODE_W, ShortcutRules.ALT), chord, 1L))
        assertEquals(ShortcutProbeStatus.LISTENING, check.status)
        assertTrue(check.receive(ShortcutRules.PALETTE, chord, chord, 1L))
        assertEquals(ShortcutProbeStatus.RECEIVED, check.status)
        assertFalse(check.receive(ShortcutRules.PALETTE, chord, chord, 1L))
    }
    @Test fun changedAndClearedBindingsInvalidateTheCheck() {
        val check = ShortcutProbe { 100L }
        listOf(null, KeyChord(KeyEvent.KEYCODE_W, ShortcutRules.ALT)).forEach { current ->
            check.begin(chord, 1L)
            assertFalse(check.receive(ShortcutRules.PALETTE, chord, current, 1L))
            assertEquals(ShortcutProbeStatus.IDLE, check.status)
        }
    }
    @Test fun deadlinesRejectLateEventsAndOldTimeoutsDoNotExpireNewChecks() {
        var clock = 100L
        val check = ShortcutProbe { clock }
        val old = check.begin(chord, 1L)
        clock += 1000
        val fresh = check.begin(chord, 1L)
        clock = 15_100
        check.expire(old); assertEquals(ShortcutProbeStatus.LISTENING, check.status)
        clock = 16_100
        assertFalse(check.receive(ShortcutRules.PALETTE, chord, chord, 1L))
        assertEquals(ShortcutProbeStatus.EXPIRED, check.status)
        check.expire(fresh); assertEquals(ShortcutProbeStatus.EXPIRED, check.status)
    }
    @Test fun cancelRemovesTheExpectedChordAndRejectsLateResults() {
        val check = ShortcutProbe { 100L }
        check.begin(chord, 1L); check.cancel()
        assertNull(check.expected); assertEquals(ShortcutProbeStatus.IDLE, check.status)
        assertFalse(check.receive(ShortcutRules.PALETTE, chord, chord, 1L))
    }
    @Test fun reconnectEpochRejectsAProbeEvenWithoutAnIntermediateFrame() {
        val connection = KeyboardServiceConnection(); val first = Any(); val second = Any()
        connection.connect(first)
        val check = ShortcutProbe { 100L }; check.begin(chord, connection.epoch)
        val initial = connection.epoch
        connection.disconnect(first); connection.connect(second)
        assertTrue(connection.connected); assertTrue(connection.epoch > initial)
        assertFalse(check.receive(ShortcutRules.PALETTE, chord, chord, connection.epoch))
        assertEquals(ShortcutProbeStatus.IDLE, check.status)
        val latest = connection.epoch; connection.disconnect(first)
        assertEquals(latest, connection.epoch); assertTrue(connection.connected)
    }
    @Test fun armedRoutingRejectsStaleConnectionsAndExpiredOrDifferentChords() {
        var clock = 100L
        val check = ShortcutProbe { clock }; check.begin(chord, 7L)
        assertFalse(check.isArmedFor(KeyChord(KeyEvent.KEYCODE_F, ShortcutRules.CTRL), chord, 7L))
        assertEquals(ShortcutProbeStatus.LISTENING, check.status)
        assertTrue(check.isArmedFor(chord, chord, 7L))
        assertFalse(check.isArmedFor(chord, chord, 8L))
        assertEquals(ShortcutProbeStatus.IDLE, check.status)
        check.begin(chord, 8L); clock = 15_100
        assertFalse(check.isArmedFor(chord, chord, 8L))
        assertEquals(ShortcutProbeStatus.EXPIRED, check.status)
    }

}
