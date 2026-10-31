package dev.tinycast.android

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.*

enum class KeyboardConsent { APPROVED, OFF, UNKNOWN }

object KeyboardServiceConsent {
    fun contains(raw: String?, accessibilityEnabled: Boolean, expected: ComponentName): Boolean = accessibilityEnabled &&
        raw?.split(':')?.mapNotNull { ComponentName.unflattenFromString(it) }?.any { it == expected } == true

    fun read(context: Context): KeyboardConsent = runCatching {
        val approved = contains(Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            Settings.Secure.getInt(context.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1,
            ComponentName(context, PaletteShortcutService::class.java))
        if (approved) KeyboardConsent.APPROVED else KeyboardConsent.OFF
    }.getOrDefault(KeyboardConsent.UNKNOWN)
}

/** The token has no Context reference; an old service cannot disconnect a newer binding. */
class KeyboardServiceConnection {
    private var owner: Any? = null
    var connected by mutableStateOf(false); private set
    internal fun connect(token: Any) { owner = token; connected = true }
    internal fun disconnect(token: Any) { if (owner === token) { owner = null; connected = false } }
}

data class ShortcutReadiness(val chord: KeyChord?, val consent: KeyboardConsent, val connected: Boolean) {
    val ready get() = chord != null && consent == KeyboardConsent.APPROVED && connected
    val title get() = when {
        chord == null -> "Choose a global shortcut"
        consent == KeyboardConsent.UNKNOWN -> "Approval status unavailable"
        consent != KeyboardConsent.APPROVED -> "Android setup required"
        !connected -> "Waiting for keyboard service"
        else -> "Ready to check shortcut"
    }
}

enum class ShortcutProbeStatus { IDLE, LISTENING, RECEIVED, EXPIRED }

/** Only filtered shortcut identities enter this check; no text or unrelated key events. */
class ShortcutProbe(private val now: () -> Long) {
    var status by mutableStateOf(ShortcutProbeStatus.IDLE); private set
    var expected by mutableStateOf<KeyChord?>(null); private set
    private var generation = 0
    private var deadline = 0L
    fun begin(chord: KeyChord): Int {
        require(ShortcutRules.error(chord) == null)
        generation++; expected = chord; deadline = now() + 15_000; status = ShortcutProbeStatus.LISTENING
        return generation
    }
    fun receive(target: String, chord: KeyChord, current: KeyChord?): Boolean {
        if (status != ShortcutProbeStatus.LISTENING) return false
        if (expected != current) { cancel(); return false }
        if (now() >= deadline) { status = ShortcutProbeStatus.EXPIRED; return false }
        if (target != ShortcutRules.PALETTE || chord != expected) return false
        status = ShortcutProbeStatus.RECEIVED
        return true
    }
    fun expire(token: Int) { if (token == generation && status == ShortcutProbeStatus.LISTENING && now() >= deadline) status = ShortcutProbeStatus.EXPIRED }
    fun cancel() { generation++; expected = null; status = ShortcutProbeStatus.IDLE }
}
