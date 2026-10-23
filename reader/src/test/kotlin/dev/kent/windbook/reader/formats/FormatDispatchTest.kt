package dev.kent.windbook.reader.formats

import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.reader.*
import dev.kent.windbook.reader.source.SourceLease
import dev.kent.windbook.testing.TestBooks
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FormatDispatchTest {
    @get:Rule val tmp = TemporaryFolder()
    @Test fun registryControlsRealEngineDispatchAndPreservesOriginalFormat() = runTest {
        val services = ReadiumServices(ApplicationProvider.getApplicationContext(), pdfFactory = null)
        val adapter = object : FormatAdapter {
            override val format = BookFormat.TXT
            override suspend fun open(source: SourceLease): Result<ReaderSession> {
                val preparation = services.sourceAccess.workspace.create()
                try {
                    val input = preparation.stage(source.openStream())
                    val epub = NormalizedPublicationWriter().write(NormalizedDocument("derived-id", "Derived title",
                        listOf(NormalizedSection("text/0001.xhtml", "Chapter", input)), emptyList()), preparation)
                    val publication = services.openPublication(epub).getOrThrow()
                    return Result.success(ReadiumReaderSession(format, publication, 1, source, preparation))
                } catch (error: Throwable) { preparation.close(); throw error }
            }
        }
        val engine = ReaderEngine(services, adapters = listOf(adapter))
        assertFalse(engine.capabilities.canRead(BookFormat.CBR))
        assertTrue(engine.capabilities.canRead(BookFormat.TXT))
        val original = tmp.newFile("source.txt").apply { writeText("<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Chapter</title></head><body><p>Derived text</p></body></html>") }
        val session = engine.open(original, BookFormat.TXT).getOrThrow()
        assertEquals(BookFormat.TXT, session.format)
        assertEquals(NavigationSurface.PUBLICATION, session.surface)
        assertEquals(1, services.sourceAccess.workspace.activeLeaseCount)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { session.close() }
        assertEquals(0, services.sourceAccess.workspace.activeLeaseCount)
        assertTrue(original.isFile)
    }
    @Test fun failedAndCancelledAdaptersReleaseTheVerifiedSource() = runTest {
        val services = ReadiumServices(ApplicationProvider.getApplicationContext(), pdfFactory = null)
        for (cancel in listOf(false, true)) {
            val source = SourceLease(TestBooks.epub(tmp.newFolder()), null, 65536)
            val adapter = object : FormatAdapter {
                override val format = BookFormat.TXT
                override suspend fun open(source: SourceLease): Result<ReaderSession> {
                    if (cancel) throw kotlinx.coroutines.CancellationException("cancel")
                    return Result.failure(IllegalArgumentException("bad input"))
                }
            }
            try { assertTrue(ReaderEngine(services, adapters = listOf(adapter)).open(source, BookFormat.TXT).isFailure) }
            catch (e: kotlinx.coroutines.CancellationException) { assertTrue(cancel) }
            assertThrows(IllegalStateException::class.java) { source.openStream() }
        }
    }
    @Test fun generatedPublicationOpensWithReadiumAndKeepsStableReadingOrder() = runTest {
        val services = ReadiumServices(ApplicationProvider.getApplicationContext(), pdfFactory = null)
        val lease = dev.kent.windbook.reader.source.SessionWorkspace(tmp.newFolder()).create()
        try {
            val input = lease.stage("<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><title>Chapter</title></head><body><p>Readable paragraph</p></body></html>".byteInputStream())
            val epub = NormalizedPublicationWriter().write(NormalizedDocument("identity", "Synthetic title", listOf(NormalizedSection("text/0001.xhtml", "Chapter", input)), emptyList()), lease)
            val publication = services.openPublication(epub).getOrThrow()
            try {
                assertEquals(listOf("text/0001.xhtml"), publication.readingOrder.map { it.href.toString().removePrefix("EPUB/") })
                assertEquals("Synthetic title", publication.metadata.title)
            } finally { publication.close() }
        } finally { lease.close() }
    }

}
