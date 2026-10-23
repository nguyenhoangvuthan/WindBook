package dev.kent.windbook.folders

import dev.kent.windbook.startStorageMaintenance
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageStartupTest {
    @Test fun cancellationAndFatalErrorsAreNotConvertedToMaintenanceSuccess() = runTest {
        val uncaught = mutableListOf<Throwable>()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.test.StandardTestDispatcher(testScheduler) +
            kotlinx.coroutines.CoroutineExceptionHandler { _, error -> uncaught.add(error) })
        val fatal = AssertionError("Fatal invariant")
        var cancellation: Throwable? = null
        startStorageMaintenance(scope, {
            kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]!!.invokeOnCompletion { cancellation = it }
            throw kotlinx.coroutines.CancellationException("Stopped")
        }, { throw fatal })
        runCurrent()
        assertTrue(cancellation is kotlinx.coroutines.CancellationException)
        org.junit.Assert.assertEquals(listOf(fatal), uncaught)
    }

    @Test fun sourceStorageFailureIsContainedWhileSiblingCompletes() = runTest {
        val uncaught = mutableListOf<Throwable>()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.test.StandardTestDispatcher(testScheduler) +
            kotlinx.coroutines.CoroutineExceptionHandler { _, error -> uncaught.add(error) })
        var folderCompleted = false
        startStorageMaintenance(scope, { folderCompleted = true }, { throw java.io.IOException("Full") })
        runCurrent()
        assertTrue(folderCompleted)
        assertTrue("Recoverable storage failure escaped startup: $uncaught", uncaught.isEmpty())
    }

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
