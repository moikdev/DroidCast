package dev.tinycast.android

import java.security.MessageDigest
import java.util.Locale

data class SearchFolder(val uri: String, val title: String, val ownsGrant: Boolean)
data class IndexedDocument(
    val id: String, val folder: String, val uri: String, val title: String,
    val path: String, val mime: String, val modified: Long
) {
    val directory get() = mime.equals("vnd.android.document/directory", ignoreCase = true)
    fun command() = Command(id, title, path, Kind.FILE, "folder", path, uri, mimeType = mime)
}

enum class FileSearchType(val label: String) {
    ALL("All types"), DOCUMENTS("Documents"), IMAGES("Images"), AUDIO("Audio"), VIDEO("Video"), ARCHIVES("Archives"), FOLDERS("Folders");
    fun accepts(document: IndexedDocument) = acceptsMime(document.mime)
    fun acceptsMime(value: String): Boolean {
        val mime = value.lowercase(Locale.ROOT)
        return when (this) {
            ALL -> true
            FOLDERS -> mime == "vnd.android.document/directory"
            IMAGES -> mime.startsWith("image/")
            AUDIO -> mime.startsWith("audio/")
            VIDEO -> mime.startsWith("video/")
            ARCHIVES -> mime in setOf("application/zip", "application/x-7z-compressed", "application/x-rar-compressed", "application/vnd.rar", "application/gzip", "application/x-tar")
            DOCUMENTS -> mime.startsWith("text/") || mime in setOf("application/pdf", "application/json", "application/rtf", "application/epub+zip", "application/msword") || mime.contains("officedocument") || mime.contains("opendocument")
        }
    }
}

object FolderIndexRules {
    const val MAX_FOLDERS = 8
    const val MAX_ENTRIES = 5000
    const val MAX_DEPTH = 16
    const val MAX_DIRECTORIES = 512
    const val MAX_CACHE_BYTES = 8 * 1024 * 1024
    const val MAX_RESULTS = 60
    fun id(authority: String, documentId: String) = "files:index:" + MessageDigest.getInstance("SHA-256")
        .digest("$authority\u0000$documentId".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    fun search(entries: List<IndexedDocument>, query: String, type: FileSearchType, checkCancelled: () -> Unit = {}): List<IndexedDocument> {
        val q = query.trim().take(256)
        val scored = ArrayList<Pair<IndexedDocument, Int>>()
        val seen = HashSet<String>()
        entries.forEachIndexed { index, entry ->
            if (index % 32 == 0) checkCancelled()
            if (seen.add(entry.id) && type.accepts(entry)) {
                val score = if (q.isEmpty()) 0 else maxOf(CommandSearch.score(q, entry.title), CommandSearch.score(q, entry.path).let { if (it < 0) -1 else it / 2 })
                if (score >= 0) scored.add(entry to score)
            }
        }
        return scored.sortedWith(compareByDescending<Pair<IndexedDocument, Int>> { it.second }
            .thenBy { it.first.title.startsWith('.') }.thenByDescending { it.first.modified }
            .thenBy { it.first.title.lowercase(Locale.ROOT) }.thenBy { it.first.id }).take(MAX_RESULTS).map { it.first }
    }
}
