package dev.tinycast.android

import android.view.KeyEvent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextRange
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.By
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PaletteStabilityDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    @Before fun ready() {
        compose.waitUntil(15000) { !compose.activity.store.loading && !compose.activity.store.folderSearch.loading }
        compose.activityRule.scenario.onActivity { it.store.reset() }
        keyboard(false)
        compose.waitForIdle()
    }
    @After fun hideKeyboard() { keyboard(false) }
    private fun bounds(tag: String): Rect = compose.onNodeWithTag(tag).fetchSemanticsNode().boundsInWindow
    private fun keyboard(show: Boolean) {
        if (show && compose.activity.resources.configuration.screenWidthDp >= 700) compose.onNodeWithContentDescription("Show on-screen keyboard").performClick()
        compose.activityRule.scenario.onActivity {
            val controller = WindowCompat.getInsetsController(it.window, it.window.decorView)
            if (show) controller.show(WindowInsetsCompat.Type.ime()) else controller.hide(WindowInsetsCompat.Type.ime())
        }
        if (show) expandGboardToolbar()
        compose.waitUntil(10000) {
            val insets = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)
            insets?.isVisible(WindowInsetsCompat.Type.ime()) == show && (!show || insets.getInsets(WindowInsetsCompat.Type.ime()).bottom > device.displayHeight / 4)
        }
        device.waitForIdle(); compose.waitForIdle()
    }
    private fun expandGboardToolbar() {
        device.waitForIdle()
        // Android 16/Gboard can acknowledge show() with its hardware-keyboard
        // toolbar. Use its real user-facing control before testing docked bounds.
        device.findObject(By.pkg("com.google.android.inputmethod.latin").desc("Show on-screen keyboard"))?.click()
    }
    private fun imeReplace(text: String, emoji: Boolean = false) {
        compose.activityRule.scenario.onActivity {
            val connection = it.window.decorView.findFocus()?.onCreateInputConnection(android.view.inputmethod.EditorInfo())
            assertNotNull("Focused Android input connection", connection)
            assertTrue(connection!!.beginBatchEdit())
            assertTrue(connection.setSelection(0, if (emoji) it.store.emoji.query.length else it.store.query.length))
            assertTrue(connection.commitText(text, 1))
            connection.endBatchEdit()
        }
        compose.waitForIdle()
        val insets = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!
        assertTrue("Docked IME must stay visible while typing", insets.isVisible(WindowInsetsCompat.Type.ime()) && insets.getInsets(WindowInsetsCompat.Type.ime()).bottom > device.displayHeight / 4)
    }
    private fun capture(name: String, settle: Boolean = true) {
        val dir = File(compose.activity.filesDir, "rigid-palette-evidence").apply { mkdirs() }
        if (settle) device.waitForIdle()
        assertTrue(device.takeScreenshot(File(dir, "$name.png")))
    }
    private fun assertOrigin(expected: Rect, tag: String) {
        val actual = bounds(tag)
        assertEquals("$tag left", expected.left, actual.left, .5f)
        assertEquals("$tag top", expected.top, actual.top, .5f)
        assertEquals("$tag width", expected.width, actual.width, .5f)
        assertEquals("$tag height", expected.height, actual.height, .5f)
        File(compose.activity.filesDir, "rigid-palette-evidence").apply { mkdirs() }
            .resolve("origins.tsv").appendText("$tag\t${actual.left}\t${actual.top}\t${actual.width}\t${actual.height}\n")
    }
    @Test fun searchOriginStaysFixedAcrossKeyboardAndTyping() {
        val origin = bounds("search")
        capture("search-keyboard-hidden")
        if (compose.activity.resources.configuration.screenWidthDp >= 700) compose.onNodeWithContentDescription("Show on-screen keyboard").performClick()
        keyboard(true)
        capture("search-keyboard-shown")
        assertOrigin(origin, "search")
        for (text in listOf("s", "se", "set", "settings", "setting", "set", "xzz-no-command", "", "12*7", "")) {
            imeReplace(text)
            assertOrigin(origin, "search")
            val first = compose.activity.store.results().firstOrNull()
            first?.let { compose.onNodeWithTag("result-${it.id}").assertIsSelected().assertIsDisplayed() }
        }
        imeReplace("12*7")
        compose.onNodeWithTag("result-result").assertTextContains("84")
        assertOrigin(origin, "search")
        capture("search-typing-settled")
        compose.activityRule.scenario.onActivity {
            // Exercise the real command executor without an IME editor action
            // that is allowed to dismiss its own keyboard.
            it.execute(it.store.results().first())
        }
        compose.waitForIdle()
        device.findObject(By.res("com.android.systemui", "dismiss_button"))?.click()
        expandGboardToolbar()
        compose.waitUntil(5000) { ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!.getInsets(WindowInsetsCompat.Type.ime()).bottom > device.displayHeight / 4 }
        assertTrue("Keyboard must remain visible while checking copy feedback", ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!.isVisible(WindowInsetsCompat.Type.ime()))
        compose.onNodeWithText("Copied to clipboard").assertIsDisplayed()
        val feedback = compose.onNodeWithText("Copied to clipboard").fetchSemanticsNode().boundsInWindow
        val imeBottom = ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!.getInsets(WindowInsetsCompat.Type.ime()).bottom
        assertTrue("Copy feedback requires a docked IME, not a zero-inset toolbar", imeBottom > device.displayHeight / 4)
        assertTrue("Copy feedback must remain above the IME", feedback.bottom <= device.displayHeight - imeBottom)
        capture("copy-feedback", settle = false)
        keyboard(false)
        assertOrigin(origin, "search")
    }
    @Test fun emojiDoesNotRequestTabletKeyboardOnEntry() {
        if (compose.activity.resources.configuration.screenWidthDp < 700) return
        keyboard(true)
        compose.onNodeWithTag("search").performTextReplacement("emoji")
        compose.onNodeWithTag("result-emoji").performClick()
        compose.waitForIdle(); device.waitForIdle()
        capture("emoji-on-entry")
        compose.waitUntil(5000) {
            !ViewCompat.getRootWindowInsets(compose.activity.window.decorView)!!.isVisible(WindowInsetsCompat.Type.ime())
        }
    }
    @Test fun emojiOriginStaysFixedAcrossKeyboardAndTyping() {
        compose.activityRule.scenario.onActivity { it.execute(Command("emoji", "Emoji", "")) }
        compose.waitUntil(10000) { !compose.activity.store.emoji.loading }
        compose.waitForIdle()
        keyboard(false)
        val origin = bounds("emoji-search")
        capture("emoji-keyboard-hidden")
        // Exercise the actual explicit tablet control rather than only an insets request.
        if (compose.activity.resources.configuration.screenWidthDp >= 700) {
            compose.onNodeWithContentDescription("Show on-screen keyboard").performClick()
        } else compose.onNodeWithTag("emoji-search").performClick()
        keyboard(true)
        capture("emoji-keyboard-shown")
        assertOrigin(origin, "emoji-search")
        for (text in listOf("f", "fa", "face", "zzz-no-emoji", "", "thumb")) {
            imeReplace(text, emoji = true)
            assertOrigin(origin, "emoji-search")
        }
        capture("emoji-typing-settled")
        keyboard(false); assertOrigin(origin, "emoji-search")
    }
    @Test fun visibleArrowSelectionDoesNotShiftResults() {
        compose.onNodeWithTag("search").performTextReplacement("settings")
        val results = compose.activity.store.results()
        assertTrue(results.size >= 2)
        val first = "result-${results[0].id}"
        val second = "result-${results[1].id}"
        val origin = bounds(first)
        compose.onNodeWithTag(second).assertIsDisplayed()
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_DOWN); compose.waitForIdle()
        compose.onNodeWithTag(second).assertIsSelected()
        capture("visible-selection")
        assertEquals("Selecting an already visible row must not scroll the list", origin, bounds(first))
    }
    @Test fun queryChangesResetBeforeNextLayoutIncludingEmptyResults() {
        compose.activityRule.scenario.onActivity { it.store.filter("Apps") }
        compose.waitForIdle()
        val first = compose.activity.store.results().first()
        val origin = bounds("result-${first.id}")
        val searchOrigin = bounds("search")
        // Start from a genuinely offscreen selection and a scrolled viewport.
        compose.activityRule.scenario.onActivity { it.store.selected = it.store.results().lastIndex }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        try {
            for (query in listOf("set", "sett", "zzz-no-application", "", "set", "")) {
                compose.activityRule.scenario.onActivity { it.store.search(query) }
                compose.mainClock.advanceTimeByFrame(); compose.waitForIdle()
                assertOrigin(searchOrigin, "search")
                val selected = compose.activity.store.results().firstOrNull()
                if (selected == null) compose.onNodeWithText("No matching commands").assertIsDisplayed()
                else {
                    compose.onNodeWithTag("result-${selected.id}").assertIsDisplayed().assertIsSelected()
                    assertEquals("First rendered query layout must start at the first row", origin.top, bounds("result-${selected.id}").top, .5f)
                }
            }
        } finally { compose.mainClock.autoAdvance = true }
        compose.onNodeWithTag("search").performTextReplacement("zzz-no-application")
        compose.onNodeWithText("No matching commands").assertIsDisplayed()
        capture("no-results")
    }
    @Test fun caretSelectionDoesNotResetCommandSelection() {
        compose.onNodeWithTag("search").performTextReplacement("settings")
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_DOWN); compose.waitForIdle()
        assertEquals(1, compose.activity.store.selected)
        compose.onNodeWithTag("search").performTextInputSelection(TextRange(1, 4))
        compose.waitForIdle()
        assertEquals("Selection-only text editing must preserve the selected command", 1, compose.activity.store.selected)
    }
}
