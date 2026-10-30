package dev.tinycast.android

import kotlinx.coroutines.sync.Mutex

/** All controllers use the same private folder files and permission ownership domain. */
internal object FolderStorageCoordinator {
    val disk = Mutex()
}
