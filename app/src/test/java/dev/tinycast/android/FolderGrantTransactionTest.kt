package dev.tinycast.android

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.ArrayDeque
import kotlin.coroutines.CoroutineContext

class FolderGrantTransactionTest {
    /** Each dispatcher return is a controllable cancellation boundary, without timing sleeps. */
    private class QueueDispatcher : CoroutineDispatcher() {
        val tasks = ArrayDeque<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.addLast(block) }
        fun next() { check(tasks.isNotEmpty()); tasks.removeFirst().run() }
    }
    private class Fixture(preexisting: Boolean = false) {
        val caller = QueueDispatcher()
        val io = QueueDispatcher()
        private val scope = CoroutineScope(SupervisorJob() + caller)
        val disk = Mutex()
        var granted = preexisting
        var saved = false
        var taken = 0
        var released = 0
        var beforeTake: () -> Unit = {}
        val ownership = FolderGrantTransaction(
            hasReadGrant = { beforeTake(); granted },
            takeReadGrant = { taken++; granted = true },
            releaseReadGrant = { released++; granted = false },
            io = io,
            storageMutex = disk
        )
        fun start(body: suspend () -> Unit) = scope.async {
            try { ownership.acquire(); body() }
            finally { ownership.releaseIfUncommitted() }
        }
        fun schedule(body: suspend () -> Unit) = scope.async { body() }
        fun drain() {
            repeat(40) {
                if (caller.tasks.isEmpty() && io.tasks.isEmpty()) return
                if (caller.tasks.isNotEmpty()) caller.next()
                if (io.tasks.isNotEmpty()) io.next()
            }
            fail("Unexpected pending coroutine work")
        }
    }

    @Test fun cancellationWhenAcquisitionReturnsRollsBackTheNewGrant() {
        val f = Fixture()
        val work = f.start { fail("Cancelled acquisition must not reach validation") }
        f.caller.next(); f.io.next() // Permission succeeded; its result is queued to the caller.
        assertTrue(f.granted)
        work.cancel(); f.drain()
        assertTrue(work.isCompleted && work.isCancelled)
        assertFalse(f.granted); assertEquals(1, f.taken); assertEquals(1, f.released)
        assertFalse(f.saved)
    }

    @Test fun cancellationWhenCommitReturnsKeepsTheSavedFoldersGrant() {
        val f = Fixture()
        val work = f.start { f.ownership.commit(f.disk) { f.saved = true } }
        f.caller.next(); f.io.next(); f.caller.next(); f.io.next()
        assertTrue(f.saved) // Durable write completed; its result has not reached the caller.
        work.cancel(); f.drain()
        assertTrue(work.isCompleted && work.isCancelled)
        assertTrue(f.granted); assertEquals(0, f.released)
    }

    @Test fun cancellationDuringThePostCommitReadKeepsTheSavedFoldersGrant() {
        val f = Fixture()
        var read = false
        lateinit var work: Deferred<Unit>
        work = f.start {
            f.ownership.commit(f.disk) { f.saved = true }
            withContext(f.io) { read = true; work.cancel() }
        }
        f.drain()
        assertTrue(read && f.saved && f.granted)
        assertTrue(work.isCompleted && work.isCancelled)
        assertEquals(0, f.released)
    }

    @Test fun cancellationCannotReleaseAPreexistingReadGrant() {
        val f = Fixture(preexisting = true)
        val work = f.start { fail("Cancelled acquisition must not reach validation") }
        f.caller.next(); f.io.next(); work.cancel(); f.drain()
        assertFalse(f.ownership.newlyAcquired)
        assertTrue(f.granted); assertEquals(0, f.released)
        assertTrue(work.isCompleted && work.isCancelled)
    }

    @Test fun cancellationBeforeTakingDoesNotAcquireOrReleaseAGrant() {
        val f = Fixture()
        lateinit var work: Deferred<Unit>
        f.beforeTake = { work.cancel() }
        work = f.start { fail("Cancelled acquisition must not reach validation") }
        f.drain()
        assertEquals(0, f.taken); assertEquals(0, f.released)
        assertFalse(f.granted); assertTrue(work.isCompleted && work.isCancelled)
    }

    @Test fun failedValidationRollsBackOnlyTheNewGrant() {
        val f = Fixture()
        val work = f.start { throw IOException("Provider metadata unavailable") }
        f.drain()
        assertThrows(IOException::class.java) { runBlocking { work.await() } }
        assertFalse(f.granted || f.saved); assertEquals(1, f.released)
    }

    @Test fun failedConfigurationWriteRollsBackTheNewGrant() {
        val f = Fixture()
        val work = f.start { f.ownership.commit(f.disk) { throw IOException("Cannot save") } }
        f.drain()
        assertThrows(IOException::class.java) { runBlocking { work.await() } }
        assertFalse(f.granted || f.saved); assertEquals(1, f.released)
    }

    @Test fun successfulCommitTransfersOwnershipToTheConfiguration() {
        val f = Fixture()
        val work = f.start { f.ownership.commit(f.disk) { f.saved = true } }
        f.drain()
        assertTrue(work.isCompleted && !work.isCancelled)
        assertTrue(f.ownership.newlyAcquired && f.saved && f.granted)
        assertEquals(0, f.released)
    }

    @Test fun cancellationWhenRemovalReturnsCannotSkipItsGrantRelease() {
        val f = Fixture(preexisting = true)
        val work = f.schedule {
            commitFolderConfiguration(f.disk, f.io,
                writeConfiguration = { f.saved = true },
                onCommitted = { f.granted = false; f.released++ })
        }
        f.caller.next(); f.io.next() // Removal committed; returning to the UI is still queued.
        work.cancel(); f.drain()
        assertTrue(work.isCompleted && work.isCancelled && f.saved)
        assertFalse(f.granted); assertEquals(1, f.released)
    }

    @Test fun failedRemovalWriteRetainsTheGrantForTheExistingConfiguration() {
        val f = Fixture(preexisting = true)
        val work = f.schedule {
            commitFolderConfiguration(f.disk, f.io,
                writeConfiguration = { throw IOException("Cannot save removal") },
                onCommitted = { f.granted = false; f.released++ })
        }
        f.drain()
        assertThrows(IOException::class.java) { runBlocking { work.await() } }
        assertTrue(f.granted); assertFalse(f.saved); assertEquals(0, f.released)
    }

    @Test fun cancelledRemovalBeforeTheWriteRetainsTheExistingGrant() {
        val f = Fixture(preexisting = true)
        val work = f.schedule {
            commitFolderConfiguration(f.disk, f.io,
                writeConfiguration = { f.saved = true },
                onCommitted = { f.granted = false; f.released++ })
        }
        f.caller.next(); work.cancel(); f.drain()
        assertTrue(work.isCompleted && work.isCancelled && f.granted)
        assertFalse(f.saved); assertEquals(0, f.released)
    }
}
