package dev.tinycast.android

import android.content.ClipboardManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.tinycast.android.extensions.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class ExtensionDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun ready() {
        compose.waitUntil(25000){compose.activity.store.extensions.runtimeReady}
        assertNull(compose.activity.store.extensions.runtimeError,compose.activity.store.extensions.runtimeError)
    }
    private fun clipboard():String {var value="";compose.activityRule.scenario.onActivity{value=it.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text.toString()};return value}
    @Test fun realRaycastStoreInstallExecuteHistoryAndUninstall(){
        val catalog=compose.activity.store.extensions
        val listing=runBlocking{catalog.search("uuid").first{it.name=="uuid-generator"}}
        runBlocking{catalog.install(listing)}
        val extension=catalog.installed.first{it.listing.id==listing.id}
        assertTrue(extension.commands.any{it.name=="generate"})
        assertTrue(File(extension.directory,"generate.js").isFile)
        compose.activityRule.scenario.onActivity{it.store.openExtension(extension,extension.commands.first{it.name=="generate"})}
        compose.onNodeWithTag("argument-numberOfUUIDsToGenerate").performTextReplacement("2")
        compose.onNodeWithTag("run-extension-setup").performClick()
        ready()
        compose.waitUntil(10000){catalog.runtimeStatus.contains("Copied",true)||catalog.runtimeStatus.contains("Generated",true)}
        val uuids=clipboard().trim().split(Regex("\\s+"))
        assertEquals(2,uuids.size)
        uuids.forEach{assertTrue(it,Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}").matches(it))}
        compose.activityRule.scenario.onActivity{it.store.extensions.close();it.store.dialog=null;it.store.openExtension(extension,extension.commands.first{it.name=="viewHistory"})}
        ready()
        compose.waitUntil(10000){catalog.runtimeTree?.toString()?.contains(uuids.first())==true}
        compose.onNodeWithTag("extension-list").assertExists()
        compose.onNodeWithTag("extension-primary").performClick()
        compose.waitForIdle()
        assertTrue(clipboard().contains('-'))
        compose.activityRule.scenario.onActivity{it.store.extensions.close();it.store.dialog=null;it.store.reset()}
        runBlocking{catalog.uninstall(extension)}
        assertFalse(catalog.installed.any{it.listing.id==listing.id})
        assertFalse(extension.directory.exists())
        assertFalse(compose.activity.store.commands().any{it.id.startsWith("ext:${listing.id}/")})
    }
    @Test fun archiveRejectsTraversal(){
        val out=ByteArrayOutputStream()
        ZipOutputStream(out).use{zip->zip.putNextEntry(ZipEntry("../escaped.txt"));zip.write("escape".toByteArray());zip.closeEntry()}
        val dir=File(compose.activity.cacheDir,"archive-test").apply{deleteRecursively();mkdirs()}
        val result=runCatching{ExtensionArchive.extract(out.toByteArray(),dir)}
        assertTrue(result.isFailure)
        assertFalse(File(dir.parentFile,"escaped.txt").exists())
        dir.deleteRecursively()
    }
    @Test fun nativeStoreUiInstallsAndRunsColorConverter(){
        compose.activityRule.scenario.onActivity{it.store.dialog="extensions"}
        compose.onNodeWithTag("store-search").performTextReplacement("oklch")
        compose.waitUntil(20000){compose.onAllNodesWithTag("install-oklch-color-converter").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("install-oklch-color-converter").performClick()
        compose.onNodeWithTag("confirm-install").performClick()
        val catalog=compose.activity.store.extensions
        compose.waitUntil(30000){(catalog.busy==null&&catalog.installed.any{it.listing.name=="oklch-color-converter"})||catalog.error!=null}
        assertNull(catalog.error,catalog.error)
        compose.onNodeWithTag("store-installed").performClick()
        compose.onNodeWithTag("store-results").performScrollToNode(hasTestTag("extension-command-convert-color"))
        compose.onNodeWithTag("extension-command-convert-color").performClick()
        ready()
        compose.onNodeWithTag("extension-search").performTextReplacement("#ff0000")
        compose.waitUntil(10000){catalog.runtimeTree?.toString()?.contains("oklch(")==true}
        compose.onNodeWithTag("extension-primary").performClick()
        compose.waitUntil(5000){catalog.runtimeStatus.contains("Cop",true)}
        assertTrue(clipboard(),clipboard().isNotBlank())
        compose.activityRule.scenario.onActivity{it.store.extensions.close();it.store.dialog=null;it.store.reset()}
    }
    @Test fun extensionsCannotReadAppPrivateFiles(){
        val id="sandbox-fixture"
        val dir=File(compose.activity.cacheDir,id).apply{mkdirs()}
        File(dir,"probe.js").writeText("module.exports.default = function(){require('fs').readFileSync('/data/data/dev.tinycast.android/shared_prefs/palette.xml','utf8')}")
        val listing=StoreExtension(id,id,"Sandbox fixture","","Test","test","https://example.com","","",1)
        val ext=InstalledExtension(listing,dir,JSONObject("""{"commands":[{"name":"probe","title":"Probe","mode":"no-view"}]}"""))
        compose.activityRule.scenario.onActivity{it.store.openExtension(ext,ext.commands.single())}
        compose.waitUntil(25000){compose.activity.store.extensions.runtimeError!=null}
        assertTrue(compose.activity.store.extensions.runtimeError!!.contains("own package and data folder"))
        compose.activityRule.scenario.onActivity{it.store.extensions.close();it.store.dialog=null}
        dir.deleteRecursively()
    }
}
