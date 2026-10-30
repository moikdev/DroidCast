package dev.tinycast.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.KeyEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

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
