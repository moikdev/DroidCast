package dev.tinycast.android

import org.junit.Assert.*
import org.junit.Test

class FolderIndexTest {
    private fun entry(name: String, path: String = name, mime: String = "text/plain", modified: Long = 0, id: String = name) =
        IndexedDocument(id, "tree", "content://fixture/$id", name, path, mime, modified)
    @Test fun identityUsesProviderAndPersistentDocumentId() {
        assertEquals(FolderIndexRules.id("provider", "doc:42"), FolderIndexRules.id("provider", "doc:42"))
        assertNotEquals(FolderIndexRules.id("provider", "doc:42"), FolderIndexRules.id("other", "doc:42"))
        assertNotEquals(FolderIndexRules.id("provider", "doc:42"), FolderIndexRules.id("provider", "doc:43"))
        assertTrue(FolderIndexRules.id("provider", "doc:42").startsWith("files:"))
    }
    @Test fun filenameMatchesOutrankPathOnlyMatches() {
        val direct = entry("invoice.pdf")
        val path = entry("notes.txt", "Invoices/notes.txt")
        assertEquals(listOf(direct, path), FolderIndexRules.search(listOf(path, direct), "invoice", FileSearchType.ALL))
    }
    @Test fun emptySearchUsesModifiedDateWithStableOrdering() {
        val a = entry("a", modified = 4); val b = entry("b", modified = 8)
        assertEquals(listOf(b, a), FolderIndexRules.search(listOf(a, b), "", FileSearchType.ALL))
    }
    @Test fun typesRespectDirectoriesAndNativeMime() {
        val image = entry("photo", mime = "image/jpeg"); val folder = entry("folder", mime = "vnd.android.document/directory")
        val pdf = entry("report", mime = "application/pdf")
        assertEquals(listOf(image), FolderIndexRules.search(listOf(image, folder, pdf), "", FileSearchType.IMAGES))
        assertEquals(listOf(folder), FolderIndexRules.search(listOf(image, folder, pdf), "", FileSearchType.FOLDERS))
        assertEquals(listOf(pdf), FolderIndexRules.search(listOf(image, folder, pdf), "", FileSearchType.DOCUMENTS))
    }
    @Test fun overlappingScopesDoNotDuplicateAnIdentity() {
        val a = entry("one", id = "same"); val b = a.copy(folder = "other", uri = "content://other")
        assertEquals(listOf(a), FolderIndexRules.search(listOf(a, b), "", FileSearchType.ALL))
    }
    @Test fun hiddenNamesRankAfterVisibleNamesAtEqualScore() {
        val hidden = entry(".log", modified = 99); val visible = entry("log", modified = 1)
        assertEquals(listOf(visible, hidden), FolderIndexRules.search(listOf(hidden, visible), "", FileSearchType.ALL))
    }
    @Test fun resultsAreBoundedAndQueriesCanBeCancelled() {
        val entries = (1..200).map { entry("file$it") }
        assertEquals(FolderIndexRules.MAX_RESULTS, FolderIndexRules.search(entries, "", FileSearchType.ALL).size)
        var checks = 0
        try { FolderIndexRules.search(entries, "file", FileSearchType.ALL) { if (++checks == 2) throw InterruptedException() }; fail("Expected cancellation") }
        catch (_: InterruptedException) { assertEquals(2, checks) }
    }
}
