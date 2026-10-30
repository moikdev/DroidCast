package dev.tinycast.android

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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

/** Keep permission bookkeeping on the same side of dispatcher cancellation as its side effect. */
internal class FolderGrantTransaction(
    private val hasReadGrant: () -> Boolean,
    private val takeReadGrant: () -> Unit,
    private val releaseReadGrant: () -> Unit,
    private val io: CoroutineDispatcher = Dispatchers.IO
) {
    var newlyAcquired = false; private set
    private var releaseOnFailure = false

    suspend fun acquire() = withContext(io) {
        val preexisting = hasReadGrant()
        ensureActive()
        takeReadGrant()
        // No suspension between successful acquisition and recording our responsibility.
        newlyAcquired = !preexisting
        releaseOnFailure = newlyAcquired
    }

    suspend fun commit(disk: Mutex, writeConfiguration: () -> Unit) = commitFolderConfiguration(
        disk, io, writeConfiguration,
        // The saved configuration owns the grant even if returning to our caller is cancelled.
        onCommitted = { releaseOnFailure = false }
    )

    suspend fun releaseIfUncommitted() {
        if (releaseOnFailure) withContext(NonCancellable + io) {
            runCatching { releaseReadGrant() }.onSuccess { releaseOnFailure = false }
        }
    }
}
