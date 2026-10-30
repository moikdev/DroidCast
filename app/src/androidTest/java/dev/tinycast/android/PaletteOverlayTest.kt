package dev.tinycast.android

import android.content.Intent
import android.provider.Settings
import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PaletteOverlayTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val device get()=UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    @Test fun compactTranslucentPaletteDismissesOutsideToPreviousApp(){
        compose.activityRule.scenario.onActivity{it.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))}
        assertTrue(device.wait(Until.hasObject(By.pkg("com.android.settings")),5000))
        device.executeShellCommand("am start -n dev.tinycast.android/.MainActivity")
        compose.waitForIdle()
        compose.onNodeWithText("Command center").assertDoesNotExist()
        val bounds=compose.onNodeWithTag("palette").fetchSemanticsNode().boundsInWindow
        if (compose.activity.resources.configuration.screenWidthDp >= 700) {
            assertTrue("Tablet palette stays compact", bounds.width < device.displayWidth * .8f)
        } else {
            assertTrue("Phone palette retains clear side margins", bounds.left > 0 && bounds.right < device.displayWidth)
            assertTrue("Phone palette uses the available width", bounds.width > device.displayWidth * .8f)
        }
        compose.activityRule.scenario.onActivity{
            val style=it.obtainStyledAttributes(intArrayOf(android.R.attr.windowIsTranslucent))
            assertTrue(style.getBoolean(0,false));style.recycle()
        }
        device.click((bounds.left-24).toInt().coerceAtLeast(1),bounds.center.y.toInt())
        compose.waitUntil(5000){compose.activityRule.scenario.state==Lifecycle.State.DESTROYED}
        assertTrue(device.wait(Until.hasObject(By.pkg("com.android.settings")),5000))
    }
    @Test fun escapeDismissesEmptyPalette(){
        compose.activityRule.scenario.onActivity{it.store.reset()};compose.waitForIdle()
        device.pressKeyCode(KeyEvent.KEYCODE_ESCAPE)
        compose.waitUntil(5000){compose.activityRule.scenario.state==Lifecycle.State.DESTROYED}
    }
    @Test fun quickSettingsTileSummonsPalette(){
        compose.activityRule.scenario.onActivity{it.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))}
        assertTrue(device.wait(Until.hasObject(By.pkg("com.android.settings")),5000))
        device.executeShellCommand("cmd statusbar add-tile dev.tinycast.android/.PaletteTileService")
        try {
            device.openQuickSettings()
            device.waitForIdle()
            device.executeShellCommand("cmd statusbar click-tile dev.tinycast.android/.PaletteTileService")
            assertTrue(device.wait(Until.hasObject(By.pkg("dev.tinycast.android")),10000))
            compose.onNodeWithTag("palette").assertExists()
        } finally {
            // A failed tile startup must not leave the shade covering the next test.
            device.executeShellCommand("cmd statusbar collapse")
        }
    }
}
