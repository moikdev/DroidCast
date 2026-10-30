package dev.tinycast.android

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Changes assistant role only on the dedicated emulator; restores its original role and consent. */
@androidx.test.filters.SdkSuppress(minSdkVersion = 29)
@RunWith(AndroidJUnit4::class)
class AssistantDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<SettingsActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private var captured = false
    private var originalRoles = emptyList<String>()
    private var originalServices = "null"
    private var originalEnabled = "null"
    private var originalAssistantSettings = emptyMap<String, String>()
    private fun shell(command: String) = device.executeShellCommand(command).trim()
    private fun roles() = shell("cmd role get-role-holders android.app.role.ASSISTANT").lineSequence().filter { it.isNotBlank() }.toList()
    @Before fun setup() {
        Assume.assumeTrue("Dedicated emulator only", shell("getprop ro.kernel.qemu") == "1")
        originalRoles = roles()
        originalServices = shell("settings get secure enabled_accessibility_services")
        originalEnabled = shell("settings get secure accessibility_enabled")
        originalAssistantSettings = listOf("assistant", "voice_interaction_service", "voice_recognition_service").associateWith { shell("settings get secure $it") }
        captured = true
        val keep = originalServices.split(':').filter { !it.startsWith(context.packageName + "/") && it != "null" }
        setSetting("enabled_accessibility_services", keep.joinToString(":").ifEmpty { "null" })
        setSetting("accessibility_enabled", if (keep.isEmpty()) "0" else originalEnabled)
        roles().filter { it == context.packageName }.forEach { shell("cmd role remove-role-holder android.app.role.ASSISTANT $it") }
        compose.activityRule.scenario.onActivity { it.selectPage("general") }
    }
    private fun setSetting(name: String, value: String) {
        require(name in setOf("enabled_accessibility_services", "accessibility_enabled", "assistant", "voice_interaction_service", "voice_recognition_service"))
        if (value == "null") shell("settings delete secure $name") else {
            require(Regex("[a-zA-Z0-9_./:]+").matches(value))
            shell("settings put secure $name $value")
        }
    }
    @After fun restore() {
        if (!captured) return
        // Close any picker before restoring the original assistant through the test shell.
        device.pressBack()
        roles().filter { it !in originalRoles }.forEach { shell("cmd role remove-role-holder android.app.role.ASSISTANT $it") }
        originalRoles.forEach { require(Regex("[a-zA-Z0-9_.]+").matches(it)); shell("cmd role add-role-holder android.app.role.ASSISTANT $it") }
        originalAssistantSettings.forEach { (name, value) -> setSetting(name, value) }
        setSetting("enabled_accessibility_services", originalServices)
        setSetting("accessibility_enabled", originalEnabled)
        assertEquals(originalRoles, roles())
        originalAssistantSettings.forEach { (name, value) -> assertEquals(value, shell("settings get secure $name")) }
        assertEquals(originalServices, shell("settings get secure enabled_accessibility_services"))
        assertEquals(originalEnabled, shell("settings get secure accessibility_enabled"))
        File(context.filesDir, "assistant-evidence").apply { mkdirs() }.resolve("restoration.json")
            .writeText("{\"assistant_role_restored\":true,\"keyboard_service_consent_restored\":true}")
    }
    private fun capture(name: String) {
        val dir = File(context.filesDir, "assistant-evidence").apply { mkdirs() }
        device.waitForIdle(); device.dumpWindowHierarchy(File(dir, "$name.xml"))
        assertTrue(device.takeScreenshot(File(dir, "$name.png")))
    }
    private fun openPicker() {
        compose.onNodeWithTag("settings-choose-assistant").performScrollTo().performClick()
        assertTrue(device.wait(Until.hasObject(By.pkg("com.android.settings")), 5000))
        val entry = device.wait(Until.findObject(By.text("Default digital assistant app")), 5000)
            ?: throw AssertionError("Default assistant setting was not shown")
        entry.click()
        assertTrue(device.wait(Until.hasObject(By.text("Droidcast")), 5000))
        capture("assistant-picker")
    }
    @Test fun assistantSelectionCancellationDoesNotChangeRoleOrEnableKeyboardService() {
        openPicker()
        device.pressBack(); device.pressBack()
        assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName)), 5000))
        assertEquals(originalRoles.filterNot { it == context.packageName }, roles())
        assertFalse(shell("settings get secure enabled_accessibility_services").contains(context.packageName))
    }
    @Test fun systemAssistantOpensThePaletteWithoutKeyboardServiceFromHomeAndSettings() {
        openPicker()
        // Android's role dialog is user-owned; choose the app and confirm its real control.
        device.findObject(By.text("Droidcast"))?.click()
        val confirm = device.wait(Until.findObject(By.text("OK")), 3000)
            ?: device.wait(Until.findObject(By.text("Set as default")), 1000)
        confirm?.click()
        compose.waitUntil(10000) { roles().contains(context.packageName) }
        assertFalse(shell("settings get secure enabled_accessibility_services").contains(context.packageName))
        for (destination in listOf("home", "settings", "warm-settings")) {
            if (destination == "home") device.pressHome()
            else context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
            assertTrue(device.wait(Until.gone(By.pkg(context.packageName)), 5000))
            if (destination == "warm-settings") shell("input keycombination 117 29") // Meta+A, the stock Android assistant shortcut.
            else device.pressKeyCode(KeyEvent.KEYCODE_ASSIST)
            assertTrue("System assistant must launch from $destination", device.wait(Until.hasObject(By.pkg(context.packageName)), 10000))
            assertTrue(device.wait(Until.hasObject(By.text("Search for apps and commands…")), 5000))
            assertFalse(shell("settings get secure enabled_accessibility_services").contains(context.packageName))
            capture("assistant-$destination")
            device.pressBack()
        }
    }
}
