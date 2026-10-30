package dev.tinycast.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext
import android.util.AtomicFile

/** Real ExternalStorageProvider grants, not simulated picker responses. */
@androidx.test.filters.SdkSuppress(minSdkVersion = 31)
@RunWith(AndroidJUnit4::class)
class FolderDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<SettingsActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val device get() = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private var original = emptyMap<String, Any?>()
    private var grants = emptySet<Uri>()
    private var files = emptyMap<String, ByteArray>()
    private var fixtureCreated = false
    private lateinit var controller: FolderSearch
    private val scopes = mutableListOf<CoroutineScope>()
    private val directory get() = File(context.filesDir, "folder-search")
    private val rootName = "DroidCastFolderFixture"
    @Before fun setup() {
        androidx.test.uiautomator.Configurator.getInstance().uiAutomationFlags = android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
        original = context.getSharedPreferences("palette", Context.MODE_PRIVATE).all.toMap()
        grants = context.contentResolver.persistedUriPermissions.map { it.uri }.toSet()
        compose.waitUntil(10000) { !compose.activity.store.folderSearch.loading }
        controller = compose.activity.store.folderSearch
        files = directory.walkTopDown().filter { it.isFile }.associate { it.relativeTo(directory).path to it.readBytes() }
        compose.activityRule.scenario.onActivity { it.selectPage("folders") }
        assertFalse("Fixture path must be unused", device.executeShellCommand("ls /sdcard/Download").lineSequence().any { it == rootName })
        fixtureCreated = true
        device.executeShellCommand("mkdir -p /sdcard/Download/$rootName/Research")
        writeFixture("Research/Orbit-notes.txt", "DroidCast scoped search fixture body 314159")
        writeFixture("Previous-note.txt", "Prior viewer fixture body 271828")
        writeFixture("Bundle.zip", "archive fixture")
    }
    @After fun restore() {
        cancelPicker()
        scopes.forEach { it.cancel() }
        compose.runOnUiThread { controller.cancelRefresh() }
        context.contentResolver.persistedUriPermissions.filter { it.uri !in grants }.forEach {
            context.contentResolver.releasePersistableUriPermission(it.uri, (if (it.isReadPermission) Intent.FLAG_GRANT_READ_URI_PERMISSION else 0) or (if (it.isWritePermission) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0))
        }
        directory.deleteRecursively(); files.forEach { (path, bytes) -> File(directory, path).apply { parentFile?.mkdirs(); writeBytes(bytes) } }
        restorePalettePreferences(context, original)
        if (fixtureCreated) device.executeShellCommand("rm -rf /sdcard/Download/$rootName")
    }
    private fun writeFixture(path: String, body: String) {
        val pipes = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommandRw("tee /sdcard/Download/$rootName/$path")
        android.os.ParcelFileDescriptor.AutoCloseOutputStream(pipes[1]).use { it.write(body.toByteArray()) }
        val echoed = android.os.ParcelFileDescriptor.AutoCloseInputStream(pipes[0]).bufferedReader().use { it.readText() }
        assertEquals(body, echoed)
    }
    private fun cancelPicker() {
        repeat(6) {
            if (device.currentPackageName != "com.google.android.documentsui") return
            device.pressBack()
            if (device.wait(Until.gone(By.pkg("com.google.android.documentsui")), 1000)) return
        }
    }
    private fun evidence(name: String, marker: String) {
        compose.onNode(hasText(marker, substring = true)).performScrollTo().assertIsDisplayed()
        compose.waitForIdle(); device.waitForIdle()
        val dir = File(context.filesDir, "folder-evidence").apply { mkdirs() }
        device.dumpWindowHierarchy(File(dir, "$name.xml")); assertTrue(device.takeScreenshot(File(dir, "$name.png")))
        assertTrue(device.wait(Until.hasObject(By.textContains(marker)), 5000))
    }
    private fun clickText(text: String) {
        val node = device.wait(Until.findObject(By.text(text)), 5000) ?: throw AssertionError("No picker control: $text")
        node.click(); device.waitForIdle()
    }
    private fun authorize() {
        compose.onNodeWithTag("folder-add").performScrollTo().performClick()
        assertTrue(device.wait(Until.hasObject(By.pkg("com.google.android.documentsui")), 7000))
        // Picker may retain a previous initial directory; choose Downloads explicitly.
        device.waitForIdle()
        if (!device.hasObject(By.res("com.google.android.documentsui:id/breadcrumb_text").text(rootName))) {
            device.findObject(By.desc("Show roots"))?.click()
            if (device.hasObject(By.text("Downloads"))) clickText("Downloads") else clickText("Download")
            clickText(rootName)
        }
        assertTrue(device.wait(Until.hasObject(By.text("USE THIS FOLDER")), 5000))
        val dir = File(context.filesDir, "folder-evidence").apply { mkdirs() }
        device.waitForIdle(); assertTrue(device.takeScreenshot(File(dir, "native-folder-picker.png")))
        clickText("USE THIS FOLDER")
        device.wait(Until.findObject(By.text("ALLOW")), 3000)?.click()
        compose.waitUntil(15000) {
            val model = compose.activity.store.folderSearch
            !model.selecting && !model.changing && !model.busy && model.folders.any { it.title == rootName }
        }
        assertNull(compose.activity.store.folderSearch.error)
        assertEquals(4, compose.activity.store.folderSearch.count)
    }
    private fun reopened(): FolderSearch {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scopes += it }
        lateinit var result: FolderSearch
        compose.runOnUiThread { result = FolderSearch(context.applicationContext as android.app.Application, scope) }
        compose.waitUntil(10000) { !result.loading }
        return result
    }
    private class QueuedIo : CoroutineDispatcher() {
        private val tasks = ConcurrentLinkedQueue<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.add(block) }
        fun runOne(): Boolean { val task = tasks.poll() ?: return false; task.run(); return true }
        fun drain() { repeat(32) { if (!runOne()) return } }
    }
    private class PausedConfig(file: File) : AtomicFile(file) {
        @Volatile var pause = false
        val opened = CountDownLatch(1)
        val finish = CountDownLatch(1)
        override fun startWrite(): FileOutputStream {
            val stream = super.startWrite()
            if (pause) {
                opened.countDown()
                if (!finish.await(15, TimeUnit.SECONDS)) {
                    super.failWrite(stream)
                    throw java.io.IOException("Test writer was not released")
                }
            }
            return stream
        }
    }
    private class FailedRenameConfig(file: File) : AtomicFile(file) {
        @Volatile var fail = false
        override fun finishWrite(stream: FileOutputStream?) {
            if (!fail) super.finishWrite(stream)
            else stream?.close() // Model AtomicFile's logged-but-not-thrown rename failure.
        }
    }
    @Test fun replacementControllerWaitsForCancelledOutgoingAtomicWriteAndGrantRelease() {
        authorize()
        val writerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scopes += it }
        val readerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scopes += it }
        val config = PausedConfig(File(directory, "config.json"))
        val readerIo = QueuedIo()
        var paletteOpened = false
        lateinit var outgoing: FolderSearch
        lateinit var replacement: FolderSearch
        compose.runOnUiThread {
            outgoing = FolderSearch(context.applicationContext as android.app.Application, writerScope,
                atomicFile = { if (it.name == "config.json") config else AtomicFile(it) })
        }
        compose.waitUntil(10000) { !outgoing.loading }
        val folder = outgoing.folders.single()
        assertTrue("Fixture grant must belong to folder search", folder.ownsGrant)
        config.pause = true
        try {
            compose.runOnUiThread { outgoing.remove(folder) }
            assertTrue("Writer must pause after startWrite", config.opened.await(5, TimeUnit.SECONDS))
            val pending = File(config.baseFile.path + ".new")
            assertTrue(pending.exists())
            compose.runOnUiThread {
                replacement = FolderSearch(context.applicationContext as android.app.Application, readerScope,
                    initialReadDispatcher = readerIo)
            }
            // Execute the replacement's actual IO entry, rather than relying on a sleep.
            assertTrue(readerIo.runOne())
            val readerWaited = replacement.loading
            val pendingPreserved = pending.exists()
            File(context.filesDir, "folder-evidence/storage-race-observation.json").writeText(org.json.JSONObject()
                .put("replacement_waited", readerWaited).put("pending_write_preserved", pendingPreserved).toString(2))
            assertTrue("Replacement must wait for outgoing storage", readerWaited)
            assertTrue("Reader must not delete the pending AtomicFile write", pendingPreserved)
            assertTrue(context.contentResolver.persistedUriPermissions.any { it.uri.toString() == folder.uri && it.isReadPermission })
            writerScope.cancel() // Settings was closed while its synchronous write was in flight.
            config.finish.countDown()
            compose.waitUntil(10000) { readerIo.drain(); !replacement.loading && !outgoing.changing }
            assertTrue(replacement.folders.isEmpty())
            assertEquals(0, replacement.count)
            assertFalse(pending.exists())
            assertEquals(0, org.json.JSONObject(config.baseFile.readText()).getJSONArray("folders").length())
            assertFalse(context.contentResolver.persistedUriPermissions.any { it.uri.toString() == folder.uri && it.isReadPermission })
            File(context.filesDir, "folder-evidence/storage-replacement.json").writeText(org.json.JSONObject()
                .put("reader_attempted_while_write_pending", true).put("pending_write_preserved", true)
                .put("outgoing_scope_cancelled", true).put("replacement_loaded_committed_removal", true)
                .put("owned_read_grant_released", true).toString(2))
            compose.activityRule.scenario.onActivity { it.openFileSearch() }
            paletteOpened = true
            compose.waitUntil(5000) {
                runCatching { compose.onNodeWithTag("file-search-status", useUnmergedTree = true).assertTextEquals("0 indexed items") }.isSuccess
            }
            assertTrue(device.wait(Until.hasObject(By.text("0 indexed items")), 5000))
            device.waitForIdle()
            assertTrue(device.takeScreenshot(File(context.filesDir, "folder-evidence/replacement-palette.png")))
        } finally {
            config.finish.countDown(); writerScope.cancel()
            compose.waitUntil(10000) { readerIo.drain(); writerScope.coroutineContext[Job]!!.children.none() }
            if (paletteOpened) compose.runOnUiThread {
                ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().toList().forEach { it.finish() }
            }
        }
    }
    @Test fun silentAtomicRenameFailureDoesNotCommitRemovalOrReleaseItsGrant() {
        authorize()
        val writerScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scopes += it }
        val config = FailedRenameConfig(File(directory, "config.json"))
        val originalConfig = config.baseFile.readText()
        lateinit var outgoing: FolderSearch
        compose.runOnUiThread {
            outgoing = FolderSearch(context.applicationContext as android.app.Application, writerScope,
                atomicFile = { if (it.name == "config.json") config else AtomicFile(it) })
        }
        compose.waitUntil(10000) { !outgoing.loading }
        val folder = outgoing.folders.single()
        config.fail = true
        compose.runOnUiThread { outgoing.remove(folder) }
        compose.waitUntil(10000) { !outgoing.changing }
        assertNotNull(outgoing.error)
        assertEquals(originalConfig, config.baseFile.readText())
        assertFalse(File(config.baseFile.path + ".new").exists())
        assertTrue(context.contentResolver.persistedUriPermissions.any { it.uri.toString() == folder.uri && it.isReadPermission })
        val replacement = reopened()
        assertEquals(listOf(folder), replacement.folders)
        assertEquals(4, replacement.count)
    }
    @Test fun scopedSearchIndexesMetadataFiltersAndReopensWithoutExportingGrants() {
        authorize()
        val model = compose.activity.store.folderSearch
        evidence("folder-configured", "4 indexed items")
        compose.runOnUiThread { model.search("Orbit", true) }
        compose.waitUntil(5000) { !model.searching && model.matches.size == 1 }
        val document = model.matches.single()
        assertEquals("Orbit-notes.txt", document.title)
        assertTrue(document.path.endsWith("Research/Orbit-notes.txt"))
        compose.runOnUiThread { model.search("Research", true); model.filterType(FileSearchType.FOLDERS) }
        compose.waitUntil(5000) { !model.searching && model.matches.size == 1 }
        assertTrue(model.matches.single().directory)
        compose.runOnUiThread { model.filterType(FileSearchType.ARCHIVES); model.search("", true) }
        compose.waitUntil(5000) { !model.searching && model.matches.size == 1 }
        val archive = model.matches.single()
        assertEquals("Bundle.zip", archive.title)
        val persisted = reopened()
        assertEquals(4, persisted.count)
        assertEquals(document.command(), persisted.command(document.id))
        assertFalse(persisted.includeInLauncher)
        compose.activityRule.scenario.onActivity {
            it.store.setAlias(document.id, "orbitfixture")
            it.store.setShortcut(document.id, KeyChord(KeyEvent.KEYCODE_O, ShortcutRules.CTRL or ShortcutRules.ALT))
            if (document.id !in it.store.pins) it.store.togglePin(document.id)
            it.store.save("files", "Unclassified recent fixture.txt", "content://dev.droidcast.test/unclassified")
        }
        val backup = BackupCodec.encode(compose.activity.store.backupSnapshot(), BackupCategory.entries.toSet())
        assertFalse(backup.contains(document.uri)); assertFalse(backup.contains(document.id)); assertFalse(backup.contains(rootName))
        val index = File(directory, "index.json").readText()
        assertFalse("Contents must not be indexed", index.contains("314159"))
        // Verify that the selected URI resolves to the current fixture before external dispatch.
        val expectedBody = "DroidCast scoped search fixture body 314159"
        val actualBody = context.contentResolver.openInputStream(Uri.parse(document.uri))!!.bufferedReader().use { it.readText() }
        assertEquals(expectedBody, actualBody)
        // Reproduce a warm viewer task: the desired document is its root, with
        // another document on top. NEW_TASK alone can surface that older top.
        fun warmViewer(uri: String, title: String, clear: Boolean) {
            context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), "text/plain")
                .setClassName("com.android.htmlviewer", "com.android.htmlviewer.HTMLViewerActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    (if (clear) Intent.FLAG_ACTIVITY_CLEAR_TASK else 0)))
            assertTrue(device.wait(Until.hasObject(By.pkg("com.android.htmlviewer")), 7000))
            assertTrue(device.wait(Until.hasObject(By.textContains(title)), 5000))
        }
        warmViewer(document.uri, document.title, true)
        val previous = persisted.configurationCommands().single { it.title == "Previous-note.txt" }
        warmViewer(previous.payload, previous.title, false)
        context.startActivity(Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        assertTrue(device.wait(Until.hasObject(By.res("settings-workspace")), 5000))
        // Leave a visible native Files view for evidence, then open the precise indexed TXT.
        compose.activityRule.scenario.onActivity { it.openFileSearch() }
        assertTrue(device.wait(Until.hasObject(By.text("All types")), 7000))
        compose.onNodeWithTag("search").assertIsDisplayed()
        compose.onNodeWithTag("file-type").performClick()
        compose.onNodeWithTag("file-type-ARCHIVES").performScrollTo().performClick()
        compose.onNodeWithTag("search").performTextReplacement("")
        compose.waitUntil(5000) {
            runCatching { compose.onNodeWithText("Bundle.zip", useUnmergedTree = true).assertIsDisplayed() }.isSuccess
        }
        assertTrue(device.wait(Until.hasObject(By.descStartsWith("Bundle.zip.")), 5000))
        assertFalse(device.hasObject(By.descStartsWith("Unclassified recent fixture.txt.")))
        compose.onNodeWithTag("file-type").performClick()
        compose.onNodeWithTag("file-type-ALL").performScrollTo().performClick()
        compose.onNodeWithTag("search").performClick().performTextReplacement("Orbit")
        compose.onNodeWithTag("search").assertTextEquals("Orbit")
        compose.onNodeWithText("Bundle.zip").assertDoesNotExist()
        assertTrue(device.wait(Until.hasObject(By.clazz("android.widget.EditText").text("Orbit")), 5000))
        assertTrue(device.wait(Until.hasObject(By.descStartsWith("Orbit-notes.txt.")), 5000))
        try {
            compose.waitUntil(5000) {
                runCatching { compose.onNodeWithTag("file-search-status", useUnmergedTree = true).assertTextEquals("4 indexed items") }.isSuccess
            }
        } catch (error: Throwable) {
            val debug = File(context.filesDir, "folder-evidence").apply { mkdirs() }
            device.takeScreenshot(File(debug, "failed-search-status.png"))
            device.dumpWindowHierarchy(File(debug, "failed-search-status.xml"))
            File(debug, "failed-search-semantics.txt").writeText(compose.onRoot(useUnmergedTree = true).printToString())
            throw error
        }
        assertTrue(device.wait(Until.hasObject(By.text("4 indexed items")), 5000))
        device.waitForIdle(); val dir = File(context.filesDir, "folder-evidence")
        assertTrue(device.takeScreenshot(File(dir, "search-result.png")))
        device.findObject(By.descStartsWith("Orbit-notes.txt.")).click()
        device.waitForIdle()
        if (!device.hasObject(By.pkg("com.android.htmlviewer"))) {
            device.wait(Until.findObject(By.text("HTML Viewer")), 3000)?.click()
            device.wait(Until.findObject(By.text("Just once")), 3000)?.click()
        }
        val viewerOpened = device.wait(Until.hasObject(By.pkg("com.android.htmlviewer")), 7000)
        if (!viewerOpened) {
            device.dumpWindowHierarchy(File(dir, "failed-viewer.xml"))
            device.takeScreenshot(File(dir, "failed-viewer.png"))
        }
        assertTrue("Indexed TXT must reach HTML Viewer", viewerOpened)
        val correctTitle = device.wait(Until.hasObject(By.textContains("Orbit-notes.txt")), 5000)
        if (!correctTitle) {
            device.dumpWindowHierarchy(File(dir, "failed-viewer-title.xml"))
            device.takeScreenshot(File(dir, "failed-viewer-title.png"))
        }
        assertTrue("Warm viewer must show the selected indexed document", correctTitle)
        assertNotNull(device.wait(Until.findObject(By.clazz("android.webkit.WebView")), 5000))
        // This stock viewer can omit its rendered body from its accessibility tree.
        // Record that limitation; the PNG's exact title/body must be visually reviewed.
        val bodyAccessible = device.wait(Until.hasObject(By.textContains("314159")), 2000)
        File(dir, "viewer-verification.json").writeText(org.json.JSONObject()
            .put("selected_uri_content_matches_fixture", true)
            .put("native_viewer_title_verified", true)
            .put("body_in_accessibility_tree", bodyAccessible)
            .put("rendered_body_requires_screenshot_review", true).toString(2))
        device.waitForIdle()
        device.dumpWindowHierarchy(File(dir, "opened-document.xml"))
        assertTrue(device.takeScreenshot(File(dir, "opened-document.png")))
    }
    @Test fun pickerCancellationOffRemovalAndRevocationPreserveRecentDocuments() {
        compose.onNodeWithTag("folder-add").performScrollTo().performClick()
        assertTrue(device.wait(Until.hasObject(By.pkg("com.google.android.documentsui")), 7000)); cancelPicker()
        compose.waitUntil(5000) { !compose.activity.store.folderSearch.selecting }
        assertTrue(compose.activity.store.folderSearch.folders.isEmpty())
        evidence("picker-cancelled", "Folder selection cancelled")
        authorize()
        val model = compose.activity.store.folderSearch
        val folder = model.folders.single { it.title == rootName }
        lateinit var recent: String
        compose.activityRule.scenario.onActivity {
            recent = it.store.save("files", "Individually picked fixture", "content://dev.droidcast.test/recent")
            model.changeEnabled(false)
        }
        compose.waitUntil(5000) { !model.changing }
        assertEquals(0, model.count); assertFalse(File(directory, "index.json").exists())
        assertNotNull(compose.activity.store.command(recent))
        assertEquals(1, model.folders.size)
        assertTrue(context.contentResolver.persistedUriPermissions.any { it.uri.toString() == folder.uri })
        evidence("search-disabled", "cached index removed")
        compose.runOnUiThread { model.changeEnabled(true) }
        compose.waitUntil(10000) { !model.changing && !model.busy && model.count == 4 }
        context.contentResolver.releasePersistableUriPermission(Uri.parse(folder.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        compose.runOnUiThread { model.revalidate() }
        compose.waitUntil(5000) { model.count == 0 && model.errors.isNotEmpty() }
        compose.onNodeWithTag("folder-status").assertTextContains("0 indexed items", substring = true)
        assertTrue(reopened().configurationCommands().isEmpty())
        evidence("permission-expired", "Folder access expired")
        // Same real picker grants the original folder again.
        authorize()
        compose.onNodeWithTag("folder-remove-${folder.uri}").performScrollTo().performClick()
        compose.onNodeWithTag("folder-remove-confirm").assertIsDisplayed()
        compose.waitForIdle()
        device.pressBack()
        compose.waitForIdle()
        assertFalse(compose.activity.isFinishing)
        compose.onNodeWithTag("folder-remove-${folder.uri}").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(1, model.folders.size)
        compose.onNodeWithTag("folder-remove-confirm").performScrollTo().performClick()
        compose.waitUntil(5000) { !model.changing && model.folders.isEmpty() }
        assertEquals(0, model.count)
        assertNotNull(compose.activity.store.command(recent))
        assertFalse(context.contentResolver.persistedUriPermissions.any { it.uri.toString() == folder.uri })
        evidence("folder-removed", "No folders selected")
    }
    @Test fun malformedMetadataCacheKeepsSelectedScopesAndCanRefresh() {
        authorize()
        File(directory, "index.json").writeText("corrupt metadata")
        val model = reopened()
        assertEquals(rootName, model.folders.single().title)
        assertEquals(0, model.count); assertNotNull(model.error)
        compose.runOnUiThread { model.refresh() }
        compose.waitUntil(10000) { !model.busy && model.count == 4 }
        assertNull(model.error)
        compose.runOnUiThread {
            model.search("Orbit", true); model.search("Bundle", true); model.filterType(FileSearchType.ARCHIVES)
        }
        compose.waitUntil(5000) { !model.searching && model.matches.size == 1 }
        assertEquals("Bundle.zip", model.matches.single().title)
    }
}
