package dev.tinycast.android

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/** The durable write and its permission handoff finish before a cancellable dispatcher return. */
internal suspend fun <T> commitFolderConfiguration(
    disk: Mutex,
    io: CoroutineDispatcher = Dispatchers.IO,
    writeConfiguration: () -> T,
    onCommitted: () -> Unit
): T = withContext(io) {
    disk.withLock {
        val result = writeConfiguration()
        onCommitted()
        result
    }
}

/** The rename may have succeeded, but the saved bytes could not be read for verification. */
internal class IndeterminateFolderWrite(cause: Exception) : IOException("Folder save could not be verified", cause)

/** Keep permission bookkeeping on the same side of dispatcher cancellation as its side effect. */
internal class FolderGrantTransaction(
    private val hasReadGrant: () -> Boolean,
    private val takeReadGrant: () -> Unit,
    private val releaseReadGrant: () -> Unit,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val storageMutex: Mutex = FolderStorageCoordinator.disk
) {
    var newlyAcquired = false; private set
    private var releaseOnFailure = false
    private var locked = false
    private val owner = Any()

    // Keep replacement readers behind validation, commit or rollback, including cancellation.
    suspend fun acquire() = withContext(io) {
        storageMutex.lock(owner)
        locked = true
        val preexisting = hasReadGrant()
        ensureActive()
        takeReadGrant()
        newlyAcquired = !preexisting
        releaseOnFailure = newlyAcquired
    }

    suspend fun commit(disk: Mutex, writeConfiguration: () -> Unit) = withContext(io) {
        check(disk === storageMutex && locked)
        try {
            writeConfiguration()
            releaseOnFailure = false
        } catch (unknown: IndeterminateFolderWrite) {
            // Never revoke a grant which may now belong to the committed configuration.
            // The caller reports the uncertain save and reopening reconciles the saved roots.
            releaseOnFailure = false
            throw unknown
        }
    }

    suspend fun releaseIfUncommitted() {
        if (locked) withContext(NonCancellable + io) {
            try {
                if (releaseOnFailure) runCatching { releaseReadGrant() }.onSuccess { releaseOnFailure = false }
            } finally {
                locked = false
                storageMutex.unlock(owner)
            }
        }
    }
}
