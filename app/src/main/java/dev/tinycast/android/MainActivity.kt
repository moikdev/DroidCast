package dev.tinycast.android

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.view.KeyEvent
import android.view.KeyboardShortcutGroup
import android.view.KeyboardShortcutInfo
import android.view.Menu
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.*

class MainActivity : ComponentActivity() {
    val store: PaletteStore by viewModels()
    var shortcutEnabled by mutableStateOf(false)
    private var sharedText by mutableStateOf<String?>(null)
    private val createBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { store.writeExport(it) }
    private val importBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { store.readBackup(it) }
    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val title = runCatching {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            }.getOrNull() ?: uri.lastPathSegment ?: "Document"
            val existing = store.files.firstOrNull { it.body == uri.toString() }
            store.save("files", title, uri.toString(), id = existing?.id)
            store.message = "Document added to recent files"
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        if (resources.configuration.smallestScreenWidthDp >= 600) window.setSoftInputMode((window.attributes.softInputMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE.inv()) or WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        if (savedInstanceState == null) receive(intent)
        setContent {
            LaunchedEffect(store.pendingShortcut, store.loading) {
                val request = store.pendingShortcut
                if (request != null && !store.loading) {
                    store.pendingShortcut = null
                    if (!ShortcutRuntime.requests.isFresh(request) || store.shortcutConfiguration.bindings[request.target] != request.chord) store.message = "Shortcut changed or expired. Try again."
                    else store.commands().firstOrNull { it.id == request.target }?.let(::execute) ?: run { store.message = "This shortcut's command is unavailable. Clear or reassign it in Keyboard shortcuts." }
                }
            }
            LaunchedEffect(sharedText) {
                sharedText?.let {
                    store.editing = SavedItem("", "Shared text", it)
                    store.dialog = "clip"
                    sharedText = null
                }
            }
            TinycastApp(store, shortcutEnabled, ::execute, ::copyText, ::enableShortcut, { finish() }, { target, chord -> ShortcutRuntime.dispatch(this, target, chord) })
        }
    }
    override fun onResume() {
        super.onResume()
        ShortcutRuntime.resumed(this)
        shortcutEnabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.split(':')?.any { it == ComponentName(this, PaletteShortcutService::class.java).flattenToString() } == true
    }
    override fun onPause() { ShortcutRuntime.paused(this); super.onPause() }
    override fun onStop() { if (!isChangingConfigurations) store.cancelShortcutRecording(); super.onStop() }
    fun acceptShortcut(token: String?, toggleExisting: Boolean = false) {
        val request = ShortcutRuntime.requests.consume(token)
        if (request == null || !ShortcutRuntime.requests.isFresh(request) || store.shortcutConfiguration.bindings[request.target] != request.chord) { store.message = "Shortcut changed or expired. Try again."; return }
        if (store.backupBusy || ShortcutRuntime.recordingOwner != null) { store.message = "Finish the current operation before running a shortcut."; return }
        if (request.target == ShortcutRules.PALETTE) { if (toggleExisting) finish() else store.reset() }
        else if (toggleExisting && request.target == "calculator" && store.dialog == null && store.category == "All" && store.query.trimStart().startsWith("=")) finish()
        else { store.reset(); store.pendingShortcut = request }
    }
    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (store.backupBusy) { store.message = "Backup in progress. Please try again when it finishes."; return }
        store.pendingShortcut = null
        store.reset(); receive(intent)
    }
    private fun receive(intent: Intent) {
        if (intent.action == ShortcutRuntime.ACTION) { acceptShortcut(intent.getStringExtra(ShortcutRuntime.TOKEN)); return }
        sharedText = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            else -> null
        }
    }
    override fun onProvideKeyboardShortcuts(data: MutableList<KeyboardShortcutGroup>?, menu: Menu?, deviceId: Int) {
        data?.add(KeyboardShortcutGroup("DroidCast", listOf(
            KeyboardShortcutInfo("Focus search", KeyEvent.KEYCODE_L, KeyEvent.META_CTRL_ON),
            KeyboardShortcutInfo("Actions", KeyEvent.KEYCODE_K, KeyEvent.META_CTRL_ON),
            KeyboardShortcutInfo("New note", KeyEvent.KEYCODE_N, KeyEvent.META_CTRL_ON),
            KeyboardShortcutInfo("Pin or unpin", KeyEvent.KEYCODE_D, KeyEvent.META_CTRL_ON)
        )))
        val titles = store.commands().associate { it.id to it.title } + (ShortcutRules.PALETTE to "Show or hide DroidCast")
        val bindings = store.shortcutConfiguration.bindings.entries.take(50).map { (id, chord) -> KeyboardShortcutInfo(titles[id] ?: "Unavailable command · $id", chord.keyCode, chord.nativeModifiers()) }
        if (bindings.isNotEmpty()) data?.add(KeyboardShortcutGroup(if (store.shortcutConfiguration.bindings.size > 50) "Configured shortcuts (first 50)" else "Configured shortcuts", bindings))
        super.onProvideKeyboardShortcuts(data, menu, deviceId)
    }
    private fun enableShortcut() { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    fun copyText(text: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("DroidCast", text))
        store.message = "Copied to clipboard"
    }
    private fun open(intent: Intent, unavailableMessage: String = "No available app can open this item") {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish()
        } catch (_: ActivityNotFoundException) {
            store.message = unavailableMessage
        } catch (_: SecurityException) {
            store.message = unavailableMessage
        }
    }
    fun openSystemSettings(destination: SettingsDestination) = open(destination.intent(packageName), "${destination.title} is unavailable on this device.")
    fun execute(command: Command) {
        if (store.backupBusy) return
        if (command.id !in setOf("backup", "backup-export", "backup-import", "backup-write", "backup-read")) store.used(command.id)
        if (command.kind == Kind.COMMAND) {
            SystemSettings.find(command.id)?.let { openSystemSettings(it); return }
        }
        when (command.kind) {
            Kind.CALC, Kind.CLIP -> copyText(command.payload)
            Kind.APP -> open(Intent.makeMainActivity(ComponentName.unflattenFromString(command.payload)))
            Kind.NOTE -> { store.editing = store.notes.firstOrNull { it.id == command.id }; store.dialog = "note" }
            Kind.LINK -> {
                if (command.payload.contains("{query}")) { store.editing = store.links.firstOrNull { it.id == command.id }; store.dialog = "link-query" }
                else open(Intent(Intent.ACTION_VIEW, Uri.parse(command.payload)))
            }
            Kind.FILE -> {
                val uri = Uri.parse(command.payload)
                runCatching { contentResolver.getType(uri) ?: "*/*" }
                    .onSuccess { type -> open(Intent(Intent.ACTION_VIEW).setDataAndType(uri, type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                    .onFailure { store.message = "Document access expired. Choose it again with Open a document." }
            }
            Kind.EXTENSION -> {
                val parts = command.payload.split('/', limit = 2)
                val extension = store.extensions.installed.firstOrNull { it.listing.id == parts[0] }
                val entry = extension?.commands?.firstOrNull { it.name == parts.getOrNull(1) }
                if (extension != null && entry != null) openExtension(extension, entry) else store.message = "This extension is no longer installed"
            }
            Kind.COMMAND -> when (command.id) {
                "backup", "backup-export" -> store.beginBackup()
                "backup-import" -> { store.beginBackup(); importBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
                "backup-write" -> store.prepareExport { createBackup.launch("DroidCast-${java.time.LocalDate.now()}.json") }
                "backup-read" -> importBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                "extensions" -> store.dialog = "extensions"
                "calculator" -> { store.category = "All"; store.search("= "); store.focusEpoch++ }
                "new-note" -> { store.editing = null; store.dialog = "note" }
                "new-link" -> { store.editing = null; store.dialog = "link" }
                "browse-files" -> openDocument.launch(arrayOf("*/*"))
                "save-clip" -> {
                    val manager = getSystemService(ClipboardManager::class.java)
                    val text = manager.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
                    if (text.isNullOrBlank()) store.message = "No clipboard text available. Copy some text first."
                    else { store.editing = SavedItem("", text.take(60).lineSequence().first(), text); store.dialog = "clip" }
                }
                "shortcuts" -> store.dialog = "shortcut-settings"
                "settings" -> open(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                "app-settings" -> store.dialog = "preferences"
                "web" -> open(Intent(Intent.ACTION_VIEW, Uri.parse(command.payload)))
            }
        }
    }
    private fun openExtension(extension: dev.tinycast.android.extensions.InstalledExtension, command: dev.tinycast.android.extensions.ExtensionCommand) { store.openExtension(extension, command) }
}
