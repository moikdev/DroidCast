package dev.tinycast.android

import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SettingsRouteDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private var original = emptyMap<String, Any?>()
    private var main: ActivityScenario<MainActivity>? = null
    @Before fun launch() {
        original = context.getSharedPreferences("palette", Context.MODE_PRIVATE).all.toMap()
        main = ActivityScenario.launch(Intent(context, MainActivity::class.java))
        compose.waitUntil(10000) { runCatching { main!!.onActivity { check(!it.store.loading && !it.store.folderSearch.loading) } }.isSuccess }
    }
    @After fun restore() {
        instrumentation.runOnMainSync {
            (ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED) +
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.STARTED)).toList().forEach { it.finish() }
        }
        main?.close()
        restorePalettePreferences(context, original)
    }
    private fun key(code: Int, meta: Int = 0) { compose.waitForIdle(); device.pressKeyCode(code, meta); compose.waitForIdle() }
    private fun settings(): SettingsActivity {
        var result: SettingsActivity? = null
        compose.waitUntil(5000) {
            instrumentation.runOnMainSync { result = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<SettingsActivity>().firstOrNull() }
            result != null
        }
        return result!!
    }
    private fun evidence(name: String, marker: String) {
        compose.waitForIdle()
        assertTrue(device.wait(Until.hasObject(By.textContains(marker)), 5000)); device.waitForIdle()
        val dir = File(context.filesDir, "settings-evidence").apply { mkdirs() }
        device.dumpWindowHierarchy(File(dir, "$name.xml")); assertTrue(device.takeScreenshot(File(dir, "$name.png")))
    }
    @Test fun ctrlCommaOpensSeparateSettingsAndPaletteReturnsWithFreshPreferences() {
        key(KeyEvent.KEYCODE_COMMA, KeyEvent.META_CTRL_ON)
        compose.onNodeWithTag("settings-workspace").assertIsDisplayed()
        assertEquals(androidx.lifecycle.Lifecycle.State.DESTROYED, main!!.state)
        compose.onNodeWithTag("settings-edit-global").performClick()
        key(KeyEvent.KEYCODE_G, KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON)
        compose.onNodeWithTag("shortcut-save").performScrollTo().performClick()
        val activity = settings()
        assertEquals("Ctrl + Alt + G", activity.store.shortcutConfiguration.bindings[ShortcutRules.PALETTE]!!.label())
        compose.onNodeWithTag("settings-return-palette").performClick()
        compose.onNodeWithTag("search").assertIsDisplayed()
        compose.waitUntil(5000) { activity.isDestroyed }
        evidence("palette-return", "Search for apps")
    }
    @Test fun configureSelectedCommandAndActionRouteOpenInlineInspector() {
        compose.onNodeWithTag("search").performTextReplacement("Create a note")
        key(KeyEvent.KEYCODE_COMMA, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
        compose.onNodeWithTag("settings-alias").performScrollTo().assertIsDisplayed()
        assertEquals("new-note", settings().requestedCommand)
        compose.onNodeWithTag("settings-return-palette").performClick()
        compose.onNodeWithTag("search").performTextReplacement("Wi-Fi settings")
        key(KeyEvent.KEYCODE_K, KeyEvent.META_CTRL_ON)
        compose.onNodeWithTag("action-Set keyboard shortcut").performClick()
        compose.onNodeWithTag("shortcut-candidate").assertIsDisplayed()
        assertEquals("system:wifi", settings().store.recordingTarget)
        compose.onAllNodes(isDialog()).assertCountEquals(0)
        evidence("configure-command-route", "Wi-Fi settings")
    }
    @Test fun aliasActionFocusesCorrectCommandAndPersistsWithoutPopup() {
        compose.onNodeWithTag("search").performTextReplacement("Create a note")
        key(KeyEvent.KEYCODE_K, KeyEvent.META_CTRL_ON)
        compose.onNodeWithTag("action-Set alias").performClick()
        compose.waitUntil(5000) { runCatching { compose.onNodeWithTag("settings-alias").assertIsFocused() }.isSuccess }
        compose.onNodeWithTag("settings-alias").assertIsFocused().performTextReplacement("settingsnote")
        var tabs = 0
        while (runCatching { compose.onNodeWithTag("settings-save-alias").assertIsFocused() }.isFailure && tabs++ < 8) key(KeyEvent.KEYCODE_TAB)
        compose.onNodeWithTag("settings-save-alias").assertIsFocused()
        key(KeyEvent.KEYCODE_ENTER)
        assertEquals("new-note", settings().requestedCommand)
        assertEquals("settingsnote", settings().store.command("new-note")!!.alias)
        compose.onAllNodes(isDialog()).assertCountEquals(0)
    }
    @Test fun settingsIsNotExportedAndOldGlobalNavigationBindingsArePreserved() {
        @Suppress("DEPRECATION") val info = context.packageManager.getActivityInfo(android.content.ComponentName(context, SettingsActivity::class.java), 0)
        assertFalse(info.exported)
        main!!.onActivity {
            it.store.setShortcut("calculator", KeyChord(KeyEvent.KEYCODE_COMMA, ShortcutRules.CTRL))
        }
        key(KeyEvent.KEYCODE_COMMA, KeyEvent.META_CTRL_ON)
        compose.onNodeWithTag("settings-workspace").assertIsDisplayed()
        assertEquals("general", settings().page)
        assertEquals(KeyChord(KeyEvent.KEYCODE_COMMA, ShortcutRules.CTRL), settings().store.shortcutConfiguration.bindings["calculator"])
        key(KeyEvent.KEYCODE_F, KeyEvent.META_CTRL_ON)
        compose.onNodeWithTag("settings-search").assertIsFocused()
    }
}
