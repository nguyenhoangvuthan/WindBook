package dev.kent.windbook.reader

import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.kent.windbook.core.model.BookSource
import dev.kent.windbook.core.model.DocumentKey
import dev.kent.windbook.reader.pdf.PdfDocumentHandle
import dev.kent.windbook.reader.pdf.PdfFixtures
import dev.kent.windbook.reader.pdf.PdfReaderSession
import dev.kent.windbook.reader.source.SessionWorkspace
import dev.kent.windbook.reader.source.SourceAccess
import dev.kent.windbook.reader.source.SourceLease
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceCleanupDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().context

    /** Holds the IO result before Main delivery, so cancellation occurs after resource construction. */
    private class MainHandoff : CoroutineDispatcher() {
        private val handler = Handler(Looper.getMainLooper())
        val hold = AtomicBoolean()
        val delivery = CompletableDeferred<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (hold.get()) check(delivery.complete(block)) else handler.post(block)
        }
        fun release(block: Runnable) { hold.set(false); handler.post(block) }
        suspend fun heartbeat() {
            val ran = CompletableDeferred<Unit>()
            handler.post { ran.complete(Unit) }
            withTimeout(2_000) { ran.await() }
        }
    }

    private suspend fun acquire(workspace: SessionWorkspace): SourceLease {
        PdfFixtures.boxed(context.cacheDir, pages = 1)
        return SourceAccess(context, workspace).acquire(BookSource.External(
            "content://dev.kent.windbook.reader.test.books/document/boxed.pdf",
            DocumentKey("dev.kent.windbook.reader.test.books", "boxed.pdf"),
        ))
    }
    private fun worker() = ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, LinkedBlockingQueue())
    private fun holdWorker(executor: ThreadPoolExecutor, release: CountDownLatch) {
        val entered = CountDownLatch(1)
        executor.execute { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
        assertTrue(entered.await(2, TimeUnit.SECONDS))
    }
    private suspend fun waitForQueuedClose(executor: ThreadPoolExecutor) {
        withTimeout(2_000) { while (executor.queue.isEmpty()) delay(5) }
    }
    private suspend fun assertReleased(workspace: SessionWorkspace) {
        withTimeout(2_000) { while (workspace.activeLeaseCount != 0) delay(5) }
        assertEquals(0L, workspace.usedBytes)
    }

    @Test fun canceledMainDeliveryKeepsSessionSourceUntilRendererTeardown() = runBlocking {
        val workspace = SessionWorkspace(File(context.cacheDir, "ordered-session-cleanup"))
        val source = acquire(workspace)
        val bytes = source.file.length()
        val executor = worker()
        val thread = executor.asCoroutineDispatcher()
        val document = PdfDocumentHandle.open(source.openDescriptor(), thread)
        val release = CountDownLatch(1)
        val handoff = MainHandoff()
        holdWorker(executor, release)
        val job = launch(handoff) {
            handoff.hold.set(true)
            openOwnedReaderSession(source) { PdfReaderSession(document, source) }
                .getOrThrow().close()
        }
        try {
            val result = withTimeout(2_000) { handoff.delivery.await() }
            job.cancel()
            handoff.release(result)
            waitForQueuedClose(executor)
            handoff.heartbeat()
            source.openStream().use { assertEquals('%'.code, it.read()) }
            assertTrue(source.file.exists())
            assertEquals(1, workspace.activeLeaseCount)
            assertEquals(bytes, workspace.usedBytes)
        } finally {
            release.countDown()
            job.cancelAndJoin()
            document.close()
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))
        }
        assertReleased(workspace)
        assertFalse(source.file.exists())
    }

    @Test fun canceledUnpublishedPdfWaitsForRendererBeforeReturningSourceOwnership() = runBlocking {
        val workspace = SessionWorkspace(File(context.cacheDir, "ordered-unpublished-cleanup"))
        val source = acquire(workspace)
        val bytes = source.file.length()
        val fd = source.openDescriptor()
        val executor = worker()
        val thread = executor.asCoroutineDispatcher()
        val release = CountDownLatch(1)
        val handoff = MainHandoff()
        val returnedToOwner = CompletableDeferred<Unit>()
        val job = launch(handoff) {
            handoff.hold.set(true)
            try { PdfDocumentHandle.open(fd, thread).close() }
            finally { source.close(); returnedToOwner.complete(Unit) }
        }
        try {
            val result = withTimeout(2_000) { handoff.delivery.await() }
            holdWorker(executor, release)
            job.cancel()
            handoff.release(result)
            waitForQueuedClose(executor)
            handoff.heartbeat()
            assertFalse("Unpublished renderer teardown must complete before its owner resumes", returnedToOwner.isCompleted)
            assertTrue(source.file.exists())
            assertEquals(1, workspace.activeLeaseCount)
            assertEquals(bytes, workspace.usedBytes)
        } finally {
            release.countDown()
            job.cancelAndJoin()
            thread.close()
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS))
        }
        assertReleased(workspace)
        assertFalse(fd.fileDescriptor.valid())
    }
}
