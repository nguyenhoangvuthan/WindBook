package dev.kent.windbook.folders

import dev.kent.windbook.startStorageMaintenance
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageStartupTest {
    @Test fun bothInitializersStartWithoutAnyScreenEvenWhenFolderCleanupIsSuspended() = runTest {
        val folderStarted = CompletableDeferred<Unit>()
        val sourceStarted = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        startStorageMaintenance(backgroundScope,
            { folderStarted.complete(Unit); gate.await() },
            { sourceStarted.complete(Unit) })
        runCurrent()
        assertTrue(folderStarted.isCompleted)
        assertTrue(sourceStarted.isCompleted)
        gate.complete(Unit)
    }
}
