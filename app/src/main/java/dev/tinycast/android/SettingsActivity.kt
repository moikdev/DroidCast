package dev.tinycast.android

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*

class SettingsActivity : ComponentActivity() {
    val store: PaletteStore by viewModels()
    var page by mutableStateOf("general"); private set
    var requestedCommand by mutableStateOf<String?>(null); private set
    var requestedEditor by mutableStateOf(""); private set
    var serviceEnabled by mutableStateOf(false); private set
    var searchFocusEpoch by mutableStateOf(0); private set
    private val navigationKeys = mutableSetOf<Pair<Int, Int>>()
    private var pendingOperation: String? = null
    private val createBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { store.writeExport(it) }
    private val importBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { store.readBackup(it) }
    private val chooseFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { store.folderSearch.selected(it) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ShortcutRuntime.settingsCreated(this)
        if (resources.configuration.smallestScreenWidthDp >= 600) window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN or android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        if (savedInstanceState == null) receive(intent) else {
            page = savedInstanceState.getString("page", "general")
            requestedCommand = savedInstanceState.getString("command")
            requestedEditor = savedInstanceState.getString("editor", "")
            pendingOperation = savedInstanceState.getString("operation")
        }
        setContent { AdvancedSettings(this) }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("page", page); outState.putString("command", requestedCommand)
        outState.putString("editor", requestedEditor); outState.putString("operation", pendingOperation)
        super.onSaveInstanceState(outState)
    }
    override fun onResume() {
        super.onResume()
        ShortcutRuntime.settingsResumed(this)
        store.folderSearch.revalidate()
        serviceEnabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)?.split(':')
            ?.contains(ComponentName(this, PaletteShortcutService::class.java).flattenToString()) == true
        if (pendingOperation == "import") { pendingOperation = null; store.beginBackup(); importBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
    }
    override fun onPause() { navigationKeys.clear(); ShortcutRuntime.settingsPaused(this); super.onPause() }
    // Public Activity/Window.Callback hook; AndroidX core's hidden base class
    // carries a class-wide restriction that lint also applies to this override.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val key = event.deviceId to event.keyCode
        if (event.action == KeyEvent.ACTION_UP && navigationKeys.remove(key)) return true
        val modifier = (event.isCtrlPressed xor event.isMetaPressed) && !event.isAltPressed
        if (event.action == KeyEvent.ACTION_DOWN && modifier && store.recordingTarget == null &&
            (event.keyCode == KeyEvent.KEYCODE_COMMA || (event.keyCode == KeyEvent.KEYCODE_F && !event.isShiftPressed))) {
            navigationKeys.add(key)
            if (event.keyCode == KeyEvent.KEYCODE_F && event.repeatCount == 0) searchFocusEpoch++
            return true
        }
        if (super.dispatchKeyEvent(event)) return true
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ESCAPE && event.asChord().modifiers == 0) {
            onBackPressedDispatcher.onBackPressed(); return true
        }
        return false
    }
    override fun onDestroy() { ShortcutRuntime.settingsDestroyed(this); super.onDestroy() }
    override fun onStop() { if (!isChangingConfigurations) store.cancelShortcutRecording(toManager = false); super.onStop() }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (store.backupBusy) { store.message = "Finish the backup operation first."; return }
        store.cancelShortcutRecording(toManager = false); receive(intent)
    }
    private fun receive(intent: Intent) {
        page = intent.getStringExtra("page")?.takeIf { it in setOf("general", "shortcuts", "folders", "backup", "about") } ?: "general"
        requestedCommand = intent.getStringExtra("command")?.takeIf(ShortcutRules::targetAllowed)
        requestedEditor = intent.getStringExtra("editor")?.takeIf { it in setOf("alias", "hotkey") }.orEmpty()
        pendingOperation = intent.getStringExtra("operation")?.takeIf { it == "import" }
        if (page == "backup") store.beginBackup()
    }
    fun selectPage(value: String) {
        if (store.backupBusy) { store.message = "Finish the backup operation first."; return }
        store.cancelShortcutRecording(toManager = false)
        page = value; requestedEditor = ""; requestedCommand = null
        if (value == "backup") store.beginBackup() else { store.discardImport(); store.dialog = null }
    }
    fun configure(target: String, editor: String = "") {
        if (store.backupBusy) { store.message = "Finish the backup operation first."; return }
        store.cancelShortcutRecording(toManager = false)
        page = "shortcuts"; requestedCommand = target; requestedEditor = editor
    }
    fun clearCommandRequest() { requestedCommand = null; requestedEditor = "" }
    fun consumeEditorRequest() { requestedEditor = "" }
    fun back() {
        when {
            store.backupBusy -> Unit
            store.recordingTarget != null -> store.cancelShortcutRecording(toManager = false)
            store.dialog == "shortcut-reset" -> store.dialog = null
            page == "backup" && store.dialog == "backup-confirm" -> store.dialog = "backup-preview"
            page == "backup" && store.backupDocument != null -> store.discardImport()
            else -> finish()
        }
    }
    fun enableService() = startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
    fun returnToPalette() { if (!store.backupBusy) { startActivity(Intent(this, MainActivity::class.java)); finish() } }
    fun openFileSearch() { if (!store.backupBusy) { startActivity(Intent(this, MainActivity::class.java).setAction("dev.droidcast.SHOW_FILES")); finish() } }
    fun command(command: Command) {
        when (command.id) {
            "open-file-search" -> openFileSearch()
            "backup-write" -> store.prepareExport { createBackup.launch("DroidCast-${java.time.LocalDate.now()}.json") }
            "backup-read" -> importBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
            "folder-add", "folder-reauthorize" -> {
                val root = command.payload.takeIf { it.isNotBlank() }
                if (store.folderSearch.beginSelection(root)) runCatching { chooseFolder.launch(root?.let(Uri::parse)) }
                    .onFailure { store.folderSearch.selected(null); store.message = "Folder chooser is unavailable." }
            }
            "web" -> runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(command.payload))) }.onFailure { store.message = "No browser is available." }
        }
    }
}
