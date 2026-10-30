package dev.tinycast.android

import android.content.ClipboardManager
import android.content.Intent
import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Configurator
import android.app.UiAutomation
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaletteDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    @Before fun reset() {
        Configurator.getInstance().uiAutomationFlags = UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
        compose.activityRule.scenario.onActivity {
            (it.store.notes + it.store.links + it.store.clips).filter { item -> item.title in listOf("Device test note", "Android docs test", "Shared clip test", "Persistence test") }.forEach { item -> it.store.delete(item.id) }
            it.store.reset()
        }
        compose.waitForIdle()
    }
    @After fun removeTestFixtures() {
        if (compose.activityRule.scenario.state == androidx.lifecycle.Lifecycle.State.DESTROYED) return
        compose.activityRule.scenario.onActivity {
            it.store.dialog = null
            (it.store.notes + it.store.links + it.store.clips + it.store.files).filter { item -> item.title in listOf("Device test note", "Android docs test", "Shared clip test", "Persistence test", "tinycast-test-image.png") }.forEach { item -> it.store.delete(item.id) }
            it.store.reset()
        }
        compose.waitForIdle()
    }
    private fun search(text: String) { compose.onNodeWithTag("search").performTextReplacement(text); compose.waitForIdle() }
    private fun key(code: Int, ctrl: Boolean = false) { device.pressKeyCode(code, if (ctrl) KeyEvent.META_CTRL_ON else 0); compose.waitForIdle() }

    @Test fun discoversRealInstalledApps() {
        compose.waitUntil(15000) { compose.activity.store.apps.isNotEmpty() }
        search("@settings")
        compose.onAllNodesWithText("Settings", useUnmergedTree = true)[0].assertIsDisplayed()
        assertTrue(compose.activity.store.results().all { it.kind == Kind.APP })
    }
    @Test fun calculatorResultCopiesToSystemClipboard() {
        search("(24 + 6) * 8")
        compose.onNodeWithTag("result-result").assertExists().performClick()
        compose.activityRule.scenario.onActivity {
            assertEquals("240", it.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text.toString())
        }
    }
    @Test fun conversionShowsAccurateResult() { search("10 km to mi"); compose.onNodeWithTag("result-result").assertTextContains("6.2137119224 mi") }
    @Test fun keyboardCreatesAndSavesNote() {
        key(KeyEvent.KEYCODE_N, true)
        compose.onNodeWithTag("editor-title").performTextReplacement("Device test note")
        compose.onNodeWithTag("editor-body").performTextReplacement("Written on the Android tablet.")
        key(KeyEvent.KEYCODE_S, true)
        compose.onNodeWithTag("editor-title").assertDoesNotExist()
        search("#Device test note")
        compose.onAllNodesWithText("Device test note", useUnmergedTree = true)[0].assertIsDisplayed()
        compose.onNodeWithTag("result-${compose.activity.store.notes.first { it.title == "Device test note" }.id}").performClick()
        compose.onNodeWithTag("editor-body").assertTextContains("Written on the Android tablet.")
        key(KeyEvent.KEYCODE_ESCAPE)
    }
    @Test fun quicklinkCreationAndArgumentEncoding() {
        search("Create a quicklink")
        compose.onNodeWithTag("result-new-link").performClick()
        compose.onNodeWithTag("editor-title").performTextReplacement("Android docs test")
        compose.onNodeWithTag("editor-body").performTextReplacement("https://developer.android.com/s/results?q={query}")
        compose.onNodeWithTag("editor-alias").performTextReplacement("docs")
        compose.onNodeWithTag("save-editor").performClick()
        search("docs compose tablet")
        compose.activityRule.scenario.onActivity {
            val result = it.store.results().first()
            assertEquals("Android docs test", result.title)
            assertEquals("https://developer.android.com/s/results?q=compose%20tablet", result.payload)
        }
    }
    @Test fun quicklinkRejectsInvalidUrl() {
        search("Create a quicklink"); compose.onNodeWithTag("result-new-link").performClick()
        compose.onNodeWithTag("editor-title").performTextReplacement("Invalid")
        compose.onNodeWithTag("editor-body").performTextReplacement("javascript:alert(1)")
        compose.onNodeWithTag("save-editor").performClick()
        compose.onNodeWithText("Enter a complete https:// or http:// URL").assertIsDisplayed()
    }
    @Test fun arrowNavigationAndPinShortcut() {
        search("Calculator")
        val before = compose.activity.store.pins.contains("calculator")
        key(KeyEvent.KEYCODE_D, true)
        assertEquals(!before, compose.activity.store.pins.contains("calculator"))
        key(KeyEvent.KEYCODE_D, true)
        search("")
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        assertEquals(1, compose.activity.store.selected)
        key(KeyEvent.KEYCODE_DPAD_UP)
        assertEquals(0, compose.activity.store.selected)
    }
    @Test fun keyboardActionsAndEscape() {
        search("Calculator"); key(KeyEvent.KEYCODE_K, true)
        compose.onNodeWithTag("action-Set alias").assertIsDisplayed()
        key(KeyEvent.KEYCODE_DPAD_DOWN)
        key(KeyEvent.KEYCODE_ESCAPE)
        compose.onNodeWithTag("action-Set alias").assertDoesNotExist()
        key(KeyEvent.KEYCODE_ESCAPE)
        assertEquals("", compose.activity.store.query)
    }
    @Test fun launchingSettingsWorks() {
        compose.activityRule.scenario.onActivity { it.startActivity(Intent(android.provider.Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)) }
        val settingsSearch = device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.pkg("com.android.settings").text("Search Settings")), 7000)
        assertNotNull("Root Settings search entry", settingsSearch)
        settingsSearch!!.click()
        assertTrue("Warm Settings search must open", device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.clazz("android.widget.EditText")), 7000))
        device.executeShellCommand("am start -W -n dev.tinycast.android/.MainActivity")
        compose.waitForIdle()
        search("Android settings"); compose.onNodeWithTag("result-settings").performClick()
        device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.pkg("com.android.settings")), 5000)
        assertEquals("com.android.settings", device.currentPackageName)
        assertTrue("Root command must leave the warm search page", device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.pkg("com.android.settings").text("Search Settings")), 7000))
        device.waitForIdle()
        val evidence = java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "shortcut-evidence").apply { mkdirs() }
        device.takeScreenshot(java.io.File(evidence, "root-settings.png"))
        device.pressBack()
    }
    @Test fun sharedTextCanBeSavedAndSearched() {
        val initialIntent = Intent(compose.activity.intent)
        compose.activityRule.scenario.onActivity {
            it.onNewIntent(Intent(it, MainActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "Useful shared tablet text"))
        }
        compose.waitUntil(5000) { compose.activity.store.dialog == "clip" }
        compose.onNodeWithTag("editor-title").performTextReplacement("Shared clip test")
        compose.onNodeWithTag("save-editor").performClick()
        search("Useful shared tablet")
        compose.onAllNodesWithText("Shared clip test", useUnmergedTree = true)[0].assertIsDisplayed()
        compose.activityRule.scenario.onActivity { it.intent = initialIntent }
    }
    @Test fun storageSurvivesRepositoryRecreation() {
        compose.activityRule.scenario.onActivity {
            val id = it.store.save("notes", "Persistence test", "Survives recreation")
            it.store.togglePin(id)
            it.store.setAlias(id, "persist")
            val recreated = PaletteStore(it.application)
            assertTrue(recreated.notes.any { n -> n.id == id && n.body == "Survives recreation" })
            assertTrue(id in recreated.pins)
            assertEquals("persist", recreated.aliases[id])
            it.store.delete(id)
        }
    }
    @Test fun actionMenuCanSetAliasAndDeleteNote() {
        var id = ""
        compose.activityRule.scenario.onActivity { id = it.store.save("notes", "Device test note", "Alias and deletion fixture") }
        search("#Device test note")
        key(KeyEvent.KEYCODE_K, true)
        compose.onNodeWithTag("action-Set alias").performClick()
        compose.onNodeWithTag("alias-field").performTextReplacement("testmemo")
        compose.onNodeWithTag("save-alias").performClick()
        search("testmemo")
        assertEquals(id, compose.activity.store.results().first().id)
        key(KeyEvent.KEYCODE_K, true)
        compose.onNodeWithTag("action-Delete from DroidCast").performClick()
        compose.onNodeWithTag("confirm-delete").performClick()
        assertFalse(compose.activity.store.notes.any { it.id == id })
        assertFalse(id in compose.activity.store.aliases)
    }
    @Test fun documentPickerGrantsAndStoresRecentFile() {
        search("Open a document")
        compose.onNodeWithTag("result-browse-files").performClick()
        assertTrue(device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.pkg("com.google.android.documentsui")), 5000))
        val menu = device.findObject(androidx.test.uiautomator.By.desc("Show roots"))
        menu?.click()
        device.waitForIdle()
        val downloads = device.findObject(androidx.test.uiautomator.UiSelector().resourceId("android:id/title").text("Downloads"))
        assertTrue("Downloads picker root", downloads.waitForExists(5000))
        downloads.click()
        assertTrue(device.wait(androidx.test.uiautomator.Until.gone(androidx.test.uiautomator.By.text("Open from")), 5000))
        val pickerSearch = device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.descContains("Search")), 5000)
        assertNotNull("Document picker search control", pickerSearch)
        pickerSearch!!.click()
        val query = device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.res("com.google.android.documentsui:id/search_src_text")), 5000)
        assertNotNull("Document picker search field", query)
        query!!.text = "tinycast-test-image.png"
        device.pressKeyCode(KeyEvent.KEYCODE_ENTER)
        val file = device.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.res("android:id/title").text("tinycast-test-image.png")), 5000)
        assertNotNull("Push the test image into /sdcard/Download before running", file)
        file!!.click()
        try {
            compose.waitUntil(5000) { compose.activity.store.files.any { it.title == "tinycast-test-image.png" } }
        } catch (error: Throwable) {
            device.takeScreenshot(java.io.File(compose.activity.filesDir, "picker-failure.png"))
            device.dumpWindowHierarchy(java.io.File(compose.activity.filesDir, "picker-failure.xml"))
            throw error
        }
        val saved = compose.activity.store.files.first { it.title == "tinycast-test-image.png" }
        compose.activityRule.scenario.onActivity {
            assertTrue(it.contentResolver.persistedUriPermissions.any { grant -> grant.uri.toString() == saved.body && grant.isReadPermission })
        }
        search("tinycast-test-image")
        compose.onNodeWithTag("result-${saved.id}").performClick()
        assertTrue(device.wait(androidx.test.uiautomator.Until.hasObject(androidx.test.uiautomator.By.pkg("com.google.android.apps.photos")), 5000))
        device.pressBack()
    }
}
