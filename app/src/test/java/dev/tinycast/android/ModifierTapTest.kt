package dev.tinycast.android

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class ModifierTapTest {
    private fun event(down: Boolean, time: Long, start: Long = time, key: Int = KeyEvent.KEYCODE_CTRL_LEFT,
                      device: Int = 7, modifiers: Int = ShortcutRules.modifierFamily(key) ?: 0, repeat: Int = 0,
                      received: Long = time, canceled: Boolean = false) =
        ShortcutEngine.Input(down, key, modifiers, repeat, device, time, start, received, canceled)
    private fun tap(detector: ModifierTapDetector, start: Long, key: Int = KeyEvent.KEYCODE_CTRL_LEFT, device: Int = 7): KeyChord? {
        assertNull(detector.handle(event(true, start, key = key, device = device)))
        return detector.handle(event(false, start + 40, start, key, device, modifiers = 0))
    }
    @Test fun twoCompletePairsDispatchExactlyOnceOnSecondRelease() {
        val detector = ModifierTapDetector()
        assertNull(tap(detector, 100))
        assertNull(detector.handle(event(true, 220)))
        assertEquals(ShortcutRules.doubleTap(ShortcutRules.CTRL, ModifierSide.LEFT), detector.handle(event(false, 260, 220)))
        assertNull(detector.handle(event(false, 260, 220)))
        assertNull(tap(detector, 400))
    }
    @Test fun allFourModifierFamiliesAndBothSidesHaveStableIdentities() {
        for (key in listOf(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT, KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT,
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT, KeyEvent.KEYCODE_META_LEFT, KeyEvent.KEYCODE_META_RIGHT)) {
            val detector = ModifierTapDetector()
            assertNull(tap(detector, 100, key))
            assertEquals(ShortcutRules.doubleTap(ShortcutRules.modifierFamily(key)!!, ShortcutRules.modifierSide(key)), tap(detector, 220, key))
        }
    }
    @Test fun eitherSideAndSpecificSideDispatchUseTheActualPersistedIdentity() {
        val left = ShortcutRules.doubleTap(ShortcutRules.CTRL, ModifierSide.LEFT)
        val right = left.copy(side = ModifierSide.RIGHT)
        val any = left.copy(side = ModifierSide.ANY)
        assertEquals("global" to any, mapOf(any to "global").targetForTap(left))
        assertEquals("global" to any, mapOf(any to "global").targetForTap(right))
        assertEquals("left" to left, mapOf(left to "left", right to "right").targetForTap(left))
        assertNull(mapOf(left to "left").targetForTap(right))
    }
    @Test fun sideConflictsAreRejectedAcrossTargetsWithoutRejectingDistinctSides() {
        val left = ShortcutRules.doubleTap(ShortcutRules.CTRL, ModifierSide.LEFT)
        val right = left.copy(side = ModifierSide.RIGHT)
        val any = left.copy(side = ModifierSide.ANY)
        ShortcutRules.validate(ShortcutConfiguration(mapOf("calculator" to left, "new-note" to right)))
        for (pair in listOf(left to any, any to right, any to any, left to left)) {
            assertTrue(ShortcutRules.overlaps(pair.first, pair.second))
            assertThrows(IllegalArgumentException::class.java) { ShortcutRules.validate(ShortcutConfiguration(mapOf("calculator" to pair.first, "new-note" to pair.second))) }
        }
        assertFalse(ShortcutRules.overlaps(left, right))
        assertFalse(ShortcutRules.overlaps(any, ShortcutRules.doubleTap(ShortcutRules.ALT)))
    }
    @Test fun malformedModifierIdentitiesFailClosed() {
        listOf(KeyChord(KeyEvent.KEYCODE_CTRL_LEFT, 0), KeyChord(KeyEvent.KEYCODE_CTRL_RIGHT, 0, ShortcutTrigger.DOUBLE_TAP),
            KeyChord(KeyEvent.KEYCODE_A, 0, ShortcutTrigger.DOUBLE_TAP), KeyChord(KeyEvent.KEYCODE_CTRL_LEFT, ShortcutRules.ALT, ShortcutTrigger.DOUBLE_TAP),
            KeyChord(KeyEvent.KEYCODE_F1, 0, side = ModifierSide.LEFT)).forEach { assertNotNull(it.toString(), ShortcutRules.error(it)) }
        assertNull(ShortcutRules.error(ShortcutRules.doubleTap(ShortcutRules.SHIFT)))
    }
    @Test fun ordinaryTypingAndChordsCancelBothBeforeAndDuringSecondPress() {
        for (duringSecond in listOf(false, true)) for (key in listOf(KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_V, KeyEvent.KEYCODE_TAB, KeyEvent.KEYCODE_ENTER)) {
            val detector = ModifierTapDetector(); tap(detector, 100)
            if (duringSecond) detector.handle(event(true, 220))
            assertNull(detector.handle(event(true, 230, key = key)))
            assertNull(detector.handle(event(false, 240, 230, key)))
            if (duringSecond) assertNull(detector.handle(event(false, 250, 220)))
            assertNull(tap(detector, 300))
        }
    }
    @Test fun ordinaryCtrlChordDownAndUpStillPassThroughTheChordEngine() {
        val detector = ModifierTapDetector(); val engine = ShortcutEngine()
        val binding = ShortcutRules.doubleTap(ShortcutRules.CTRL)
        for (input in listOf(event(true, 100), event(true, 120, key = KeyEvent.KEYCODE_C),
            event(false, 150, 120, KeyEvent.KEYCODE_C), event(false, 160, 100))) {
            assertNull(detector.handle(input))
            assertEquals(ShortcutEngine.Result(false), engine.handle(input, mapOf(binding to "global")))
        }
    }
    @Test fun holdsAndTooSlowSecondPressNeverDispatch() {
        for (firstLong in listOf(true, false)) {
            val detector = ModifierTapDetector()
            detector.handle(event(true, 100)); assertNull(detector.handle(event(false, if (firstLong) 351 else 140, 100)))
            detector.handle(event(true, 500)); assertNull(detector.handle(event(false, if (firstLong) 540 else 751, 500)))
        }
        val detector = ModifierTapDetector(); tap(detector, 100)
        assertNull(tap(detector, 491))
    }
    @Test fun inclusiveTimingBoundariesAreDeterministic() {
        val detector = ModifierTapDetector()
        detector.handle(event(true, 100)); assertNull(detector.handle(event(false, 350, 100)))
        detector.handle(event(true, 700))
        assertEquals(ShortcutRules.doubleTap(ShortcutRules.CTRL, ModifierSide.LEFT), detector.handle(event(false, 950, 700)))
    }
    @Test fun repeatsCancelFirstAndSecondTap() {
        for (second in listOf(false, true)) {
            val detector = ModifierTapDetector()
            if (second) tap(detector, 100)
            detector.handle(event(true, 220)); assertNull(detector.handle(event(true, 230, 220, repeat = 1)))
            assertNull(detector.handle(event(false, 260, 220)))
            assertNull(tap(detector, 300))
        }
    }
    @Test fun differentDeviceOrDifferentPhysicalModifierCannotCompleteAPair() {
        val detector = ModifierTapDetector(); tap(detector, 100)
        assertNull(tap(detector, 220, device = 8))
        assertNull(tap(detector, 300, key = KeyEvent.KEYCODE_CTRL_RIGHT))
        assertNull(tap(detector, 400, key = KeyEvent.KEYCODE_ALT_LEFT))
    }
    @Test fun simultaneousModifiersAndAdditionalMetaStateCancel() {
        for (extra in listOf(ShortcutRules.ALT, ShortcutRules.SHIFT, ShortcutRules.META)) {
            val detector = ModifierTapDetector(); tap(detector, 100)
            assertNull(detector.handle(event(true, 220, modifiers = ShortcutRules.CTRL or extra)))
            assertNull(detector.handle(event(false, 260, 220)))
        }
        val detector = ModifierTapDetector(); detector.handle(event(true, 100))
        detector.handle(event(true, 110, key = KeyEvent.KEYCODE_CTRL_RIGHT))
        assertNull(detector.handle(event(false, 140, 100)))
        assertNull(detector.handle(event(false, 150, 110, KeyEvent.KEYCODE_CTRL_RIGHT)))
        assertNull(tap(detector, 200))
    }
    @Test fun missingMismatchedAndDuplicateReleasesCannotActivate() {
        val detector = ModifierTapDetector()
        detector.handle(event(true, 100)); detector.handle(event(true, 220))
        assertNull(detector.handle(event(false, 260, 220)))
        assertNull(tap(detector, 300))
        assertNull(detector.handle(event(false, 360, 300)))
        assertNull(tap(detector, 400))
        detector.clear(); detector.handle(event(true, 100))
        assertNull(detector.handle(event(false, 140, 99)))
        assertNull(tap(detector, 200))
    }
    @Test fun cancellationStaleDeliveryAndNonmonotonicTimesClearPendingGesture() {
        for (bad in listOf(event(true, 220, canceled = true), event(true, 220, received = 1221), event(true, 220, received = 219),
            event(true, 99), event(false, 220, 230))) {
            val detector = ModifierTapDetector(); tap(detector, 100)
            assertNull(detector.handle(bad)); assertNull(tap(detector, 400))
        }
    }
    @Test fun explicitResetDiscardsPartialGestureAcrossFocusConfigurationOrConnectionChanges() {
        val detector = ModifierTapDetector(); tap(detector, 100); detector.clear()
        assertNull(tap(detector, 220))
        detector.handle(event(true, 300)); detector.clear()
        assertNull(detector.handle(event(false, 340, 300)))
        assertNull(tap(detector, 400))
    }
    @Test fun bindingAndConnectionEpochChangesDiscardEarlierTap() {
        val binding = ShortcutRules.doubleTap(ShortcutRules.CTRL)
        for (changeBinding in listOf(false, true)) {
            val observer = ModifierShortcutObserver()
            val original = mapOf(binding to "calculator")
            observer.handle(event(true, 100), original, epoch = 1)
            observer.handle(event(false, 140, 100), original, epoch = 1)
            val next = if (changeBinding) mapOf(binding to "new-note") else original
            val epoch = if (changeBinding) 1L else 3L
            assertNull(observer.handle(event(true, 220), next, epoch = epoch))
            assertNull(observer.handle(event(false, 260, 220), next, epoch = epoch))
        }
    }
    @Test fun exactlyOneOfServiceAndForegroundObserversCanDispatchTheSameEvents() {
        val binding = ShortcutRules.doubleTap(ShortcutRules.CTRL)
        val config = mapOf(binding to "calculator")
        for (serviceConnected in listOf(false, true)) {
            val service = ModifierShortcutObserver(); val foreground = ModifierShortcutObserver()
            val calls = mutableListOf<Pair<String, KeyChord>>()
            for (input in listOf(event(true, 100), event(false, 140, 100), event(true, 220), event(false, 260, 220))) {
                service.handle(input, config, enabled = serviceConnected, epoch = 1)?.let(calls::add)
                foreground.handle(input, config, enabled = !serviceConnected, epoch = 1)?.let(calls::add)
            }
            assertEquals(listOf("calculator" to binding), calls)
        }
    }
    @Test fun pauseAndOwnershipTransferNeverCompleteAPartialTap() {
        val binding = ShortcutRules.doubleTap(ShortcutRules.CTRL)
        val config = mapOf(binding to "calculator")
        val service = ModifierShortcutObserver(); val local = ModifierShortcutObserver()
        for (input in listOf(event(true, 100), event(false, 140, 100))) {
            assertNull(local.handle(input, config, true, 1))
        }
        for (input in listOf(event(true, 220), event(false, 260, 220))) {
            assertNull(local.handle(input, config, false, 2))
            assertNull(service.handle(input, config, true, 2))
        }
        service.handle(event(true, 300), config, false, 2)
        service.handle(event(false, 340, 300), config, true, 2)
        assertNull(service.handle(event(true, 400), config, true, 2))
        assertNull(service.handle(event(false, 440, 400), config, true, 2))
    }
    @Test fun imeWindowChangeDoesNotCancelButAppAndUnknownWindowsDo() {
        val binding = ShortcutRules.doubleTap(ShortcutRules.CTRL)
        val config = mapOf(binding to "calculator")
        for (window in listOf("android.inputmethodservice.SoftInputWindow", "dev.example.OtherActivity", "android.app.Dialog", null)) {
            val observer = ModifierShortcutObserver()
            observer.handle(event(true, 100), config)
            observer.handle(event(false, 140, 100), config)
            observer.windowChanged(window)
            observer.handle(event(true, 220), config)
            val actual = observer.handle(event(false, 260, 220), config)
            if (window == "android.inputmethodservice.SoftInputWindow") assertEquals("calculator" to binding, actual)
            else assertNull(actual)
        }
    }
    @Test fun enteringAndLeavingRecorderWithoutKeyEventsStillDiscardsEarlierTap() {
        val binding = ShortcutRules.doubleTap(ShortcutRules.CTRL)
        val config = mapOf(binding to "calculator")
        val observer = ModifierShortcutObserver()
        observer.handle(event(true, 100), config, recordingEpoch = 0)
        observer.handle(event(false, 140, 100), config, recordingEpoch = 0)
        // No key event arrives while recording is active; enter/exit advances the epoch twice.
        observer.handle(event(true, 220), config, recordingEpoch = 2)
        assertNull(observer.handle(event(false, 260, 220), config, recordingEpoch = 2))
        observer.handle(event(true, 300), config, recordingEpoch = 2)
        assertEquals("calculator" to binding, observer.handle(event(false, 340, 300), config, recordingEpoch = 2))
    }

    @Test fun delayedOldWindowNotificationCannotCancelNewerKeysFromCapturedTrace() {
        val observer = ModifierShortcutObserver()
        val binding = ShortcutRules.doubleTap(ShortcutRules.CTRL)
        val config = mapOf(binding to ShortcutRules.PALETTE)
        observer.handle(event(true, 19391691, received = 19391695), config)
        observer.handle(event(false, 19391731, 19391691, received = 19391732), config)
        observer.handle(event(true, 19391812, received = 19391813), config)
        // Captured Home notification was sent before the first key, but delivered here.
        observer.windowChanged("com.google.android.apps.nexuslauncher.NexusLauncherActivity", 19391288)
        assertEquals(ShortcutRules.PALETTE to binding,
            observer.handle(event(false, 19391856, 19391812, received = 19391857), config))
    }
    @Test fun windowChangesDuringEitherTapStillCancelIncludingUnknownAndEqualTime() {
        val binding = ShortcutRules.doubleTap(ShortcutRules.CTRL)
        val config = mapOf(binding to "calculator")
        for (time in listOf<Long?>(null, -1L, 0L, 100L, 120L, 150L, 230L)) {
            val observer = ModifierShortcutObserver()
            observer.handle(event(true, 100), config)
            observer.handle(event(false, 140, 100), config)
            observer.handle(event(true, 220), config)
            observer.windowChanged(null, time)
            assertNull("Window event $time", observer.handle(event(false, 260, 220), config))
        }
    }
    @Test fun delayedWindowNotificationBeforeFirstKeyAlsoPreservesAnInProgressFirstPress() {
        val observer = ModifierShortcutObserver()
        val binding = ShortcutRules.doubleTap(ShortcutRules.CTRL)
        val config = mapOf(binding to "calculator")
        observer.handle(event(true, 100), config)
        observer.windowChanged("android.app.Dialog", 99)
        observer.handle(event(false, 140, 100), config)
        observer.handle(event(true, 220), config)
        assertEquals("calculator" to binding, observer.handle(event(false, 260, 220), config))
    }

}
