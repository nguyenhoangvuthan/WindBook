package dev.kent.windbook.reader

import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.BookSource
import dev.kent.windbook.core.model.DocumentKey
import dev.kent.windbook.reader.pdf.PdfDocumentHandle
import dev.kent.windbook.reader.pdf.PdfFixtures
import dev.kent.windbook.reader.source.*
import dev.kent.windbook.testing.TestBooks
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalSourceDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.context
    private fun source(name: String) = BookSource.External(
        DocumentsContract.buildDocumentUri("dev.kent.windbook.reader.test.books", name).toString(),
        DocumentKey("dev.kent.windbook.reader.test.books", name),
    )
    private fun stats(name: String = "") = context.contentResolver.call(Uri.parse("content://dev.kent.windbook.reader.test.books"), "stats", name, null)!!
    private suspend fun assertProviderClosed() {
        withTimeout(2_000) { while (stats().getInt("active") != 0) delay(10) }
    }
    private fun workspace() = SessionWorkspace(File(context.cacheDir, "test-reader-sessions"))

    @Test fun externalEpubRetainsImporterArchiveSafetyBoundary() = runBlocking {
        val original = TestBooks.epub(context.cacheDir, "safe-original.epub")
        val unsafe = File(context.cacheDir, "unsafe.epub")
        java.util.zip.ZipFile(original).use { input ->
            java.util.zip.ZipOutputStream(unsafe.outputStream()).use { output ->
                input.entries().asSequence().forEach { entry ->
                    val copy = java.util.zip.ZipEntry(entry.name)
                    if (entry.method == java.util.zip.ZipEntry.STORED) {
                        copy.method = entry.method; copy.size = entry.size
                        copy.compressedSize = entry.size; copy.crc = entry.crc
                    }
                    output.putNextEntry(copy)
                    input.getInputStream(entry).use { it.copyTo(output) }
                    output.closeEntry()
                }
                output.putNextEntry(java.util.zip.ZipEntry("../ignored.txt"))
                output.write("ignored".toByteArray()); output.closeEntry()
            }
        }
        val workspace = workspace()
        val engine = ReaderEngine(ReadiumServices(context, null), SourceAccess(context, workspace))
        val result = engine.open(source("unsafe.epub"), BookFormat.EPUB)
        result.getOrNull()?.close()
        assertTrue("Folder EPUBs must pass the same archive checks as imports", result.isFailure)
        assertEquals(0, workspace.activeLeaseCount)
        assertProviderClosed()
    }

    @Test fun verifiedMetadataUsesOneAcquisitionAndKeepsLeaseOpen() = runBlocking {
        TestBooks.epub(context.cacheDir)
        val access = SourceAccess(context, workspace())
        val uri = source("book.epub")
        val before = stats("book.epub").getInt("attempts")
        access.acquire(uri).use { lease ->
            val hash = lease.fingerprint()
            val metadata = ReadiumSourceMetadata(ReadiumServices(context, null)).read(lease, BookFormat.EPUB).getOrThrow()
            assertEquals("Test Book", metadata.title)
            assertEquals(hash, lease.fingerprint())
            ReaderEngine(ReadiumServices(context, null), access).open(lease, BookFormat.EPUB).getOrThrow().close()
        }
        assertEquals(1, stats("book.epub").getInt("attempts") - before)
        assertProviderClosed()
    }

    @Test fun pdfMetadataCoverBoundsLongestEdgeBeforeRendering() = runBlocking {
        for ((width, height) in listOf(4000 to 100, 100 to 4000)) {
            val file = PdfFixtures.boxed(context.cacheDir, "cover.pdf", pages = 1, pageW = width, pageH = height)
            val bytes = pdfCoverWebp(file)!!
            val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            assertTrue("Cover is ${options.outWidth}x${options.outHeight}", options.outWidth in 1..600 && options.outHeight in 1..600)
        }
    }

    @Test fun defaultWorkspaceCleanupWaitsForIOAcquisition() = runBlocking {
        val orphan = File(context.cacheDir, "reader-sessions/orphan-init").apply { mkdirs() }
        val access = withContext(Dispatchers.Main) { SourceAccess(context) }
        assertTrue("Constructing the resolver on main must not delete session files", orphan.exists())
        val owned = File(context.cacheDir, "owned-source").apply { writeText("source") }
        access.acquire(BookSource.Owned(owned.path)).use { assertEquals("source", it.openStream().reader().use { r -> r.readText() }) }
        assertFalse(orphan.exists())
        access.workspace.create().use { active ->
            val staged = active.stage(java.io.ByteArrayInputStream(byteArrayOf(1)))
            orphan.mkdirs()
            access.initialize(); access.initialize()
            assertFalse(orphan.exists())
            assertTrue(staged.exists())
            assertEquals(1, access.workspace.activeLeaseCount)
        }
    }

    @Test fun pinnedReadiumContentResourceOpensUriAndReopensForRandomReads() = runBlocking {
        TestBooks.epub(context.cacheDir)
        val name = "book.epub"
        val before = stats(name).getInt("attempts")
        val started = android.os.SystemClock.elapsedRealtime()
        val publication = ReadiumServices(context, null).openPublication(Uri.parse(source(name).uri)).getOrThrow()
        try {
            assertEquals("Test Book", publication.metadata.title)
            val resource = publication.get(publication.readingOrder.first())!!
            try { assertTrue(resource.read().getOrNull()!!.toString(Charsets.UTF_8).contains("Hello WindBook")) }
            finally { resource.close() }
            android.util.Log.i("WBSourceGate", "directUriMs=${android.os.SystemClock.elapsedRealtime() - started}, providerOpens=${stats(name).getInt("attempts") - before}")
            assertTrue(stats(name).getInt("attempts") > before)
        } finally { publication.close() }
        assertProviderClosed()
    }

    @Test fun externalEpubAndPdfKeepContentAndReleaseSessionFiles() = runBlocking {
        TestBooks.epub(context.cacheDir)
        PdfFixtures.boxed(context.cacheDir, pages = 1)
        val workspace = workspace()
        val access = SourceAccess(context, workspace)
        val engine = ReaderEngine(ReadiumServices(context, pdfFactory = null), access)
        val before = File(context.filesDir, "books").walkTopDown().filter { it.isFile }.count()
        val beforeEpubOpens = stats("book.epub").getInt("attempts")
        val started = android.os.SystemClock.elapsedRealtime()
        val epub = engine.open(source("book.epub"), BookFormat.EPUB).getOrThrow() as ReadiumReaderSession
        assertEquals("Test Book", epub.publication.metadata.title)
        assertTrue(epub.positionCount > 0)
        val content = epub.publication.readingOrder.first()
        val resource = epub.publication.get(content)!!
        val bytes = resource.read().getOrNull()!!
        assertTrue(bytes.toString(Charsets.UTF_8).contains("Hello WindBook"))
        resource.close()
        assertEquals(1, stats("book.epub").getInt("attempts") - beforeEpubOpens)
        android.util.Log.i("WBSourceGate", "epubMs=${android.os.SystemClock.elapsedRealtime() - started}, cacheBytes=${workspace.usedBytes}")
        epub.close()
        val pdf = engine.open(source("boxed.pdf"), BookFormat.PDF).getOrThrow() as PdfSession
        assertEquals(1, pdf.pageCount)
        assertEquals(400f, pdf.firstPageSizes().first().width, 0.1f)
        pdf.close()
        assertEquals(0, workspace.activeLeaseCount)
        assertEquals(0L, workspace.usedBytes)
        assertProviderClosed()
        assertEquals(before, File(context.filesDir, "books").walkTopDown().filter { it.isFile }.count())
    }

    @Test fun pipePdfStagesAndReplacementAfterAcquisitionCannotChangeFingerprintOrReading() = runBlocking {
        val file = PdfFixtures.boxed(context.cacheDir, pages = 1)
        val original = file.readBytes()
        val workspace = workspace()
        val access = SourceAccess(context, workspace)
        val acquired = access.acquire(source("pipe-boxed.pdf"))
        PdfFixtures.boxed(context.cacheDir, pages = 3)
        assertEquals(MessageDigest.getInstance("SHA-256").digest(original).joinToString("") { "%02x".format(it) }, acquired.fingerprint())
        acquired.openStream().use { a -> acquired.openStream().use { b -> assertEquals(a.read(), b.read()); assertEquals(a.read(), b.read()) } }
        acquired.openDescriptor().use { a -> acquired.openDescriptor().use { b ->
            ParcelFileDescriptor.AutoCloseInputStream(a).use { aa -> ParcelFileDescriptor.AutoCloseInputStream(b).use { bb ->
                assertEquals(aa.read(), bb.read()); assertEquals(aa.read(), bb.read())
            } }
        } }
        val session = ReaderEngine(ReadiumServices(context, null), access).open(acquired, BookFormat.PDF).getOrThrow() as PdfSession
        assertEquals(1, session.pageCount)
        session.close()
        assertEquals(0, workspace.activeLeaseCount)
    }

    @Test fun deniedAndCorruptOpenDoNotRetryAndReleaseAllLeases() = runBlocking {
        val workspace = workspace()
        val engine = ReaderEngine(ReadiumServices(context, null), SourceAccess(context, workspace))
        assertTrue(engine.open(source("denied-boxed.pdf"), BookFormat.PDF).isFailure)
        File(context.cacheDir, "bad.pdf").writeText("corrupt")
        assertTrue(engine.open(source("bad.pdf"), BookFormat.PDF).isFailure)
        assertEquals(0, workspace.activeLeaseCount)
        assertEquals(0L, workspace.usedBytes)
        val fd = ParcelFileDescriptor.open(File(context.cacheDir, "bad.pdf"), ParcelFileDescriptor.MODE_READ_ONLY)
        assertTrue(runCatching { PdfDocumentHandle.open(fd) }.isFailure)
        assertFalse(fd.fileDescriptor.valid())
        assertEquals(1, stats("denied-boxed.pdf").getInt("attempts"))
        assertEquals(1, stats("bad.pdf").getInt("attempts"))
        assertProviderClosed()
    }

    @Test fun cancelBlockedPipeReleasesAcquisitionPromptly() = runBlocking {
        val workspace = workspace()
        val job = launch(Dispatchers.IO) { SourceAccess(context, workspace).acquire(source("stalled-book.pdf")).close() }
        try {
            withTimeout(2_000) { while (stats("stalled-book.pdf").getInt("attempts") == 0) delay(10) }
            job.cancel()
            withTimeout(1_500) { job.join() }
            assertEquals(0, workspace.activeLeaseCount)
        } finally {
            context.contentResolver.call(Uri.parse("content://dev.kent.windbook.reader.test.books"), "release", null, null)
            job.cancelAndJoin()
        }
        assertProviderClosed()
    }

    @Test fun externalUnknownLengthAndQuotasReleasePartialSnapshots() = runBlocking {
        File(context.cacheDir, "large.pdf").writeBytes(ByteArray(100))
        val root = File(context.cacheDir, "limited-sessions")
        val workspace = SessionWorkspace(root, SourceLimits(20, 30, 10, 4)) { 100 }
        val access = SourceAccess(context, workspace)
        for (name in listOf("large.pdf", "pipe-large.pdf")) {
            assertTrue(runCatching { access.acquire(source(name)) }.exceptionOrNull() is SourceTooLargeException)
            assertEquals(0, workspace.activeLeaseCount)
            assertEquals(0L, workspace.usedBytes)
        }
        val lowSpace = SessionWorkspace(root, SourceLimits(200, 300, 10, 4)) { 12 }
        assertTrue(runCatching { SourceAccess(context, lowSpace).acquire(source("large.pdf")) }.exceptionOrNull() is SourceSpaceException)
        assertEquals(0, lowSpace.activeLeaseCount)
        assertProviderClosed()
    }

    @Test fun canceledEngineOpenConsumesAcquiredLeaseAndMainThreadCloseCleansAsynchronously() = runBlocking {
        PdfFixtures.boxed(context.cacheDir, pages = 1)
        val workspace = workspace()
        val access = SourceAccess(context, workspace)
        val engine = ReaderEngine(ReadiumServices(context, null), access)
        repeat(10) {
            val acquired = access.acquire(source("boxed.pdf"))
            val job = launch(start = CoroutineStart.UNDISPATCHED) {
                engine.open(acquired, BookFormat.PDF).getOrThrow().close()
            }
            job.cancelAndJoin()
            assertEquals(0, workspace.activeLeaseCount)
        }
        val session = engine.open(source("boxed.pdf"), BookFormat.PDF).getOrThrow()
        val violations = java.util.concurrent.CopyOnWriteArrayList<android.os.strictmode.Violation>()
        withContext(Dispatchers.Main) {
            val oldPolicy = android.os.StrictMode.getThreadPolicy()
            android.os.StrictMode.setThreadPolicy(android.os.StrictMode.ThreadPolicy.Builder()
                .detectDiskWrites().penaltyListener(java.util.concurrent.Executor { it.run() }) { violations.add(it) }.build())
            try { session.close(); session.close() }
            finally { android.os.StrictMode.setThreadPolicy(oldPolicy) }
        }
        withTimeout(2_000) { while (workspace.activeLeaseCount != 0) delay(10) }
        assertTrue(violations.isEmpty())
        assertProviderClosed()
    }

    @Test fun canceledPdfOpenConsumesDescriptorAndLeavesNoWorker() = runBlocking {
        val file = PdfFixtures.boxed(context.cacheDir, pages = 1)
        val before = Thread.getAllStackTraces().keys.count { it.name == "windbook-pdf" && it.isAlive }
        repeat(10) {
            val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val job = launch(start = CoroutineStart.UNDISPATCHED) { PdfDocumentHandle.open(fd).close() }
            job.cancelAndJoin()
            assertFalse(fd.fileDescriptor.valid())
        }
        delay(100)
        assertEquals(before, Thread.getAllStackTraces().keys.count { it.name == "windbook-pdf" && it.isAlive })
    }
}
