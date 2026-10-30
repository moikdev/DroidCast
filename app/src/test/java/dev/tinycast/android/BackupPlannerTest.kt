package dev.tinycast.android

import org.junit.Assert.*
import org.junit.Test

class BackupPlannerTest {
    private val note = SavedItem("notes:one", "Travel", "Tablet workflow")
    private val categories = BackupCategory.entries.toSet()
    private fun plan(old: BackupData, incoming: BackupData, policy: RestorePolicy = RestorePolicy.MERGE, selected: Set<BackupCategory> = categories, defaults: Map<String, String> = emptyMap()) = BackupPlanner.plan(old, BackupDocument(incoming, categories), selected, policy, defaults)

    @Test fun mergeIsIdempotentAndKeepsConflictingIdentity() {
        val incoming = BackupData(notes = listOf(note, SavedItem("notes:two", "Other", "New")))
        val first = plan(BackupData(notes = listOf(note.copy(body = "Keep edited text"))), incoming)
        assertEquals("Keep edited text", first.data.notes[0].body)
        assertEquals(1, first.added); assertEquals(1, first.skipped)
        assertEquals(first.data, plan(first.data, incoming).data)
    }
    @Test fun contentDuplicatesRemapPinsAliasesAndLearning() {
        val old = BackupData(notes = listOf(note))
        val incoming = BackupData(notes = listOf(note.copy(id = "notes:copy")), pins = setOf("notes:copy"), aliases = mapOf("notes:copy" to "travel"), usage = mapOf("notes:copy" to 7))
        val result = plan(old, incoming).data
        assertEquals(listOf(note), result.notes)
        assertEquals(setOf(note.id), result.pins)
        assertEquals(mapOf(note.id to "travel"), result.aliases)
        assertEquals(mapOf(note.id to 7), result.usage)
    }
    @Test fun replacementTouchesOnlySelectedCategories() {
        val old = BackupData(notes = listOf(note), clips = listOf(SavedItem("clips:one", "Keep", "clip")), pins = setOf("calculator"))
        val result = plan(old, BackupData(), RestorePolicy.REPLACE, setOf(BackupCategory.NOTES))
        assertTrue(result.data.notes.isEmpty()); assertEquals(old.clips, result.data.clips); assertEquals(old.pins, result.data.pins)
        assertEquals(1, result.removed)
    }
    @Test fun preferencesMergeKeepsExistingOverridesAndRank() {
        val result = plan(BackupData(aliases = mapOf("calculator" to "math"), usage = mapOf("calculator" to 8)), BackupData(aliases = mapOf("calculator" to "count"), usage = mapOf("calculator" to 2)))
        assertEquals("math", result.data.aliases["calculator"]); assertEquals(8, result.data.usage["calculator"])
    }
    @Test fun conflictingEffectiveAliasesRejectTheWholePlan() {
        val incoming = BackupData(links = listOf(SavedItem("links:one", "Docs", "https://example.com", "calc")))
        assertThrows(IllegalArgumentException::class.java) { plan(BackupData(), incoming, defaults = mapOf("calculator" to "calc")) }
    }
    @Test fun clipMergeCannotSilentlyTrimExistingData() {
        val clips = (1..100).map { SavedItem("clips:$it", "$it", "$it") }
        assertThrows(IllegalArgumentException::class.java) { plan(BackupData(clips = clips), BackupData(clips = listOf(SavedItem("clips:new", "New", "text")))) }
    }
    @Test fun noOrUnavailableCategoryCannotBeApplied() {
        assertThrows(IllegalArgumentException::class.java) { plan(BackupData(), BackupData(), selected = emptySet()) }
        assertThrows(IllegalArgumentException::class.java) { BackupPlanner.plan(BackupData(), BackupDocument(BackupData(), setOf(BackupCategory.NOTES)), categories, RestorePolicy.MERGE, emptyMap()) }
    }
    @Test fun remappedAliasesCannotSilentlyDiscardConflictingValues() {
        val incoming = BackupData(notes = listOf(note, note.copy(id = "notes:copy")), aliases = mapOf(note.id to "travel", "notes:copy" to "trip"))
        assertThrows(IllegalArgumentException::class.java) { plan(BackupData(), incoming) }
        assertEquals(listOf(note), plan(BackupData(), incoming, selected = setOf(BackupCategory.NOTES)).data.notes)
    }
    @Test fun remappedRankingUsesMaximumRatherThanInputOrder() {
        val incoming = BackupData(notes = listOf(note, note.copy(id = "notes:copy")), usage = linkedMapOf(note.id to 8, "notes:copy" to 2))
        assertEquals(mapOf(note.id to 8), plan(BackupData(), incoming).data.usage)
    }
}
