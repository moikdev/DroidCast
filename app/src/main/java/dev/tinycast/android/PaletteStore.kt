package dev.tinycast.android

import android.app.Application
import android.content.Intent
import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import dev.tinycast.android.extensions.*

data class SavedItem(val id: String, val title: String, val body: String, val alias: String = "")

class PaletteStore(application: Application) : AndroidViewModel(application) {
    val extensions = ExtensionCatalog(application)
    private val prefs = application.getSharedPreferences("palette", Context.MODE_PRIVATE)
    var apps by mutableStateOf<List<Command>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var notes by mutableStateOf(read("notes")); private set
    var links by mutableStateOf(read("links")); private set
    var clips by mutableStateOf(read("clips")); private set
    var files by mutableStateOf(read("files")); private set
    var pins by mutableStateOf(prefs.getStringSet("pins", setOf("calculator", "new-note", "browse-files", "new-link"))!!.toSet()); private set
    var usage by mutableStateOf(readMap("usage")); private set
    var aliases by mutableStateOf(readAliases()); private set
    var query by mutableStateOf("")
    var category by mutableStateOf("All")
    var selected by mutableIntStateOf(0)
    var focusEpoch by mutableIntStateOf(0)
    var dialog by mutableStateOf<String?>(null)
    var editing by mutableStateOf<SavedItem?>(null)
    var message by mutableStateOf<String?>(null)

    init { refreshApps() }

    fun refreshApps() {
        viewModelScope.launch {
            apps = withContext(Dispatchers.IO) {
                val context = getApplication<Application>()
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                @Suppress("DEPRECATION")
                context.packageManager.queryIntentActivities(intent, 0)
                    .filter { it.activityInfo.packageName != context.packageName }
                    .distinctBy { "${it.activityInfo.packageName}/${it.activityInfo.name}" }
                    .map { Command("app:${it.activityInfo.packageName}/${it.activityInfo.name}", it.loadLabel(context.packageManager).toString(), "Open application", Kind.APP, "app", it.activityInfo.packageName, "${it.activityInfo.packageName}/${it.activityInfo.name}") }
                    .sortedBy { it.title.lowercase() }
            }
            loading = false
        }
    }

    fun commands(): List<Command> = (listOf(
        Command("calculator", "Calculator", "Math, percentages & unit conversions", icon = "calculator", keywords = "math convert units temperature length weight", alias = "calc"),
        Command("new-note", "Create a note", "Capture a thought without breaking your flow", icon = "note", keywords = "write markdown new memo", alias = "note"),
        Command("browse-files", "Open a document", "Browse device & connected storage", icon = "folder", keywords = "file document storage picker", alias = "files"),
        Command("new-link", "Create a quicklink", "Turn a website or search into a command", icon = "link", keywords = "url custom bookmark new", alias = "link"),
        Command("save-clip", "Save clipboard text", "Keep the current clip in your local collection", icon = "clipboard", keywords = "copy paste history save", alias = "clip"),
        Command("shortcuts", "Keyboard shortcuts", "A faster way around your command palette", icon = "keyboard", keywords = "help hotkey keys ctrl space global", alias = "keys"),
        Command("settings", "Android settings", "Open device settings", icon = "settings", keywords = "android system device configuration preferences", alias = "settings"),
        Command("app-settings", "DroidCast preferences", "Global shortcut, privacy & app information", icon = "settings", keywords = "accessibility global keyboard permission setup", alias = "prefs")
    ) + SystemSettings.commands() + listOf(Command("extensions", "Raycast Store", "Find, install & manage extensions", icon = "extension", keywords = "plugins addons marketplace extensions store", alias = "store")) + apps +
        extensions.installed.flatMap { extension -> extension.commands.map { cmd -> Command("ext:${extension.listing.id}/${cmd.name}", cmd.title, extension.listing.title, Kind.EXTENSION, "extension", "${extension.listing.title} ${cmd.description}", "${extension.listing.id}/${cmd.name}") } } +
        notes.map { Command(it.id, it.title, it.body.lineSequence().firstOrNull().orEmpty().ifBlank { "Empty note" }, Kind.NOTE, "note", it.body, it.body) } +
        links.map { Command(it.id, it.title, it.body, Kind.LINK, "link", it.body, it.body, it.alias) } +
        clips.map { Command(it.id, it.title, "Saved text · Enter to copy", Kind.CLIP, "clipboard", it.body, it.body) } +
        files.map { Command(it.id, it.title, "Recent document · permission granted by you", Kind.FILE, "folder", it.title, it.body) }
    ).map { it.copy(alias = aliases[it.id] ?: it.alias) }

    fun results(): List<Command> {
        val prefixKind = when (query.firstOrNull()) { '@' -> Kind.APP; '#' -> Kind.NOTE; ':' -> Kind.LINK; '>' -> Kind.COMMAND; else -> null }
        val q = if (prefixKind != null) query.drop(1).trimStart() else query
        val filter = prefixKind ?: when (category) { "Apps" -> Kind.APP; "Notes" -> Kind.NOTE; "Quicklinks" -> Kind.LINK; "Clips" -> Kind.CLIP; "Files" -> Kind.FILE; "Extensions" -> Kind.EXTENSION; else -> null }
        val all = commands().filter { filter == null || it.kind == filter }.filter { category != "Pinned" || it.id in pins }
        val result = CommandSearch.find(all, q, pins, usage).toMutableList()
        if (filter == null && category != "Pinned") {
            Calculator.evaluate(q)?.let { result.add(0, Command("result", it.result, it.expression, Kind.CALC, "calculator", payload = it.result)) }
            val word = q.substringBefore(' ')
            all.firstOrNull { it.kind == Kind.LINK && it.alias.isNotBlank() && it.alias.equals(word, true) && q.contains(' ') }?.let {
                result.removeAll { c -> c.id == it.id }
                result.add(0, it.copy(subtitle = "Search for ${q.substringAfter(' ')}", payload = it.payload.replace("{query}", android.net.Uri.encode(q.substringAfter(' ')))))
            }
            if (q.isNotBlank() && Calculator.evaluate(q) == null && !q.startsWith('=')) result.add(Command("web", "Search the web", q, icon = "search", payload = "https://www.google.com/search?q=${android.net.Uri.encode(q)}"))
        }
        return if(q.isBlank()) result.sortedWith(compareBy<Command> { if(it.id in pins) 0 else 1 }.thenBy { if(it.id in pins) -1 else it.kind.ordinal }) else result
    }

    fun search(value: String) { query = value; selected = 0 }
    fun openExtension(extension: InstalledExtension, command: ExtensionCommand) {
        extensions.launch(extension, command)
        val prefs = extensions.preferences(extension, command)
        val needsSetup = command.definition.optJSONArray("arguments").objects().isNotEmpty() || extension.fields(command).any { it.optBoolean("required") && (!prefs.has(it.getString("name")) || prefs.optString(it.getString("name")).isBlank()) }
        dialog = if (needsSetup) "extension-setup" else "extension-run"
    }
    fun installExtension(listing: StoreExtension) { viewModelScope.launch {
        try { extensions.install(listing); message = "${listing.title} installed" }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Throwable) { extensions.error = e.message ?: "Installation failed" }
    } }
    fun uninstallExtension(extension: InstalledExtension) { viewModelScope.launch {
        try { extensions.uninstall(extension) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Throwable) { extensions.error = e.message ?: "Uninstallation failed" }
    } }
    fun filter(value: String) { category = value; search(""); focusEpoch++ }
    fun reset() { category = "All"; search(""); dialog = null; focusEpoch++ }
    fun used(id: String) { usage = usage + (id to ((usage[id] ?: 0) + 1)); prefs.edit().putString("usage", JSONObject(usage).toString()).apply() }
    fun togglePin(id: String) { pins = if (id in pins) pins - id else pins + id; prefs.edit().putStringSet("pins", pins).apply() }
    fun setAlias(id: String, value: String) {
        val alias = value.trim().lowercase()
        require(alias.isEmpty() || Regex("[a-z][a-z0-9_-]{0,23}").matches(alias)) { "Use one word: letters, numbers, _ or -" }
        require(alias.isEmpty() || commands().none { it.id != id && it.alias == alias }) { "That alias is already in use" }
        aliases = aliases + (id to alias)
        prefs.edit().putString("aliases", JSONObject(aliases).toString()).apply()
    }

    fun save(kind: String, title: String, body: String, alias: String = "", id: String? = null): String {
        val item = SavedItem(id ?: "$kind:${UUID.randomUUID()}", title.trim(), body, alias.trim())
        require(item.title.isNotEmpty()) { "Give it a name first" }
        if (kind == "links") {
            val uri = android.net.Uri.parse(body.replace("{query}", "test"))
            require(uri.scheme in listOf("https", "http") && !uri.host.isNullOrBlank()) { "Enter a complete https:// or http:// URL" }
            require(alias.isBlank() || Regex("[a-z][a-z0-9_-]{0,23}").matches(alias)) { "Alias must be one lowercase word" }
            require(alias.isBlank() || commands().none { it.id != id && it.alias == alias }) { "That alias is already in use" }
            aliases = aliases - item.id
            prefs.edit().putString("aliases", JSONObject(aliases).toString()).apply()
        }
        val old = when (kind) { "notes" -> notes; "links" -> links; "clips" -> clips; else -> files }
        val next = (listOf(item) + old.filter { it.id != item.id }).let { if (kind == "clips") it.take(100) else it }
        write(kind, next)
        when (kind) { "notes" -> notes = next; "links" -> links = next; "clips" -> clips = next; else -> files = next }
        return item.id
    }
    fun delete(id: String) {
        listOf("notes", "links", "clips", "files").forEach { key ->
            val next = read(key).filter { it.id != id }; write(key, next)
            when (key) { "notes" -> notes = next; "links" -> links = next; "clips" -> clips = next; else -> files = next }
        }
        pins = pins - id; prefs.edit().putStringSet("pins", pins).apply()
        aliases = aliases - id; prefs.edit().putString("aliases", JSONObject(aliases).toString()).apply()
        selected = 0
    }
    private fun read(key: String): List<SavedItem> = runCatching {
        val array = JSONArray(prefs.getString(key, "[]"))
        (0 until array.length()).map { array.getJSONObject(it).let { o -> SavedItem(o.getString("id"), o.getString("title"), o.getString("body"), o.optString("alias")) } }
    }.getOrDefault(emptyList())
    private fun write(key: String, items: List<SavedItem>) {
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("id", it.id).put("title", it.title).put("body", it.body).put("alias", it.alias)) }
        prefs.edit().putString(key, array.toString()).apply()
    }
    private fun readMap(key: String): Map<String, Int> = runCatching { val o = JSONObject(prefs.getString(key, "{}")!!); o.keys().asSequence().associateWith { o.getInt(it) } }.getOrDefault(emptyMap())
    private fun readAliases(): Map<String, String> = runCatching { val o = JSONObject(prefs.getString("aliases", "{}")!!); o.keys().asSequence().associateWith { o.getString(it) } }.getOrDefault(emptyMap())
}
