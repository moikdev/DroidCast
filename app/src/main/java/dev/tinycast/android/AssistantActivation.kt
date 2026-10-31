package dev.tinycast.android

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.ActivityNotFoundException
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/** System assistant entry, independent of keyboard accessibility consent. */
class DroidcastInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        setDisabledShowContext(VoiceInteractionSession.SHOW_WITH_ASSIST or VoiceInteractionSession.SHOW_WITH_SCREENSHOT)
    }
}

class DroidcastSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = PaletteAssistantSession(this)
}

private class PaletteAssistantSession(context: Context) : VoiceInteractionSession(context) {
    override fun onPrepareShow(args: Bundle?, showFlags: Int) {
        super.onPrepareShow(args, showFlags)
        setUiEnabled(false)
        setDisabledShowContext(SHOW_WITH_ASSIST or SHOW_WITH_SCREENSHOT)
    }
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        try {
            startAssistantActivity(Intent(context, MainActivity::class.java).setAction(Intent.ACTION_ASSIST))
        } catch (_: Exception) {
            android.widget.Toast.makeText(context, "Droidcast could not open. Use the app icon or Quick Settings tile.", android.widget.Toast.LENGTH_SHORT).show()
        } finally { finish() }
    }
}

internal object AssistantActivation {
    enum class NavigationLaunch { NAVIGATION, ANDROID_SETTINGS, UNAVAILABLE }
    fun isSelected(context: Context): Boolean = if (Build.VERSION.SDK_INT >= 29) {
        context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
    } else Settings.Secure.getString(context.contentResolver, "assistant")?.substringBefore('/') == context.packageName

    // ASSISTANT is not requestable through RoleManager on stock Android 16.
    // Use the system's digital-assistant settings, where the user owns the selection.
    fun selectionIntent(): Intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)

    // AOSP's navigation page is not a public Android action on every OEM.
    // Fall back to the real Settings app while keeping manual directions inline.
    fun openNavigationSettings(launch: (Intent) -> Unit): NavigationLaunch {
        for ((action, result) in listOf(
            "com.android.settings.NAVIGATION_MODE_SETTINGS" to NavigationLaunch.NAVIGATION,
            Settings.ACTION_SETTINGS to NavigationLaunch.ANDROID_SETTINGS
        )) {
            try {
                val intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (result == NavigationLaunch.NAVIGATION) intent.setPackage("com.android.settings")
                launch(intent)
                return result
            } catch (_: ActivityNotFoundException) {
                // This device can still offer the standard Settings entry.
            } catch (_: SecurityException) {
                // OEM-specific pages may exist but reject external callers.
            }
        }
        return NavigationLaunch.UNAVAILABLE
    }
}
