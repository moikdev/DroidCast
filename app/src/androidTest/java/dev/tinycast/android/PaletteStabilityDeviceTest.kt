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
        compose.activityRule.scenario.onActivity {
            val controller = WindowCompat.getInsetsController(it.window, it.window.decorView)
            if (show) controller.show(WindowInsetsCompat.Type.ime()) else controller.hide(WindowInsetsCompat.Type.ime())
        }
        compose.waitUntil(10000) {
            ViewCompat.getRootWindowInsets(compose.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime()) == show
        }
        device.waitForIdle(); compose.waitForIdle()
    }
    private fun capture(name: String) {
        val dir = File(compose.activity.filesDir, "rigid-palette-evidence").apply { mkdirs() }
        device.waitForIdle()
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
        keyboard(true)
        capture("search-keyboard-shown")
        assertOrigin(origin, "search")
        for (text in listOf("s", "se", "set", "settings", "setting", "set", "xzz-no-command", "", "12*7", "")) {
            compose.onNodeWithTag("search").performTextReplacement(text)
            compose.waitForIdle()
            assertOrigin(origin, "search")
            val first = compose.activity.store.results().firstOrNull()
            first?.let { compose.onNodeWithTag("result-${it.id}").assertIsSelected().assertIsDisplayed() }
        }
        compose.onNodeWithTag("search").performTextReplacement("12*7")
        compose.onNodeWithTag("result-result").assertTextContains("84")
        assertOrigin(origin, "search")
        capture("search-typing-settled")
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
            compose.onNodeWithTag("emoji-search").performTextReplacement(text)
            compose.waitForIdle(); assertOrigin(origin, "emoji-search")
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
