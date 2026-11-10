package dev.kent.windbook.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Debug
import android.os.SystemClock
import android.provider.DocumentsContract
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.core.model.BookSource
import dev.kent.windbook.core.model.DocumentKey
import dev.kent.windbook.reader.pdf.PdfFixtures
import dev.kent.windbook.reader.source.ReadiumSourceMetadata
import dev.kent.windbook.reader.source.SourceAccess
import dev.kent.windbook.testing.TestBooks
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Fixed synthetic corpus; raw stage observations are benchmark evidence, not timing assertions. */
@RunWith(AndroidJUnit4::class)
class CoverPreparationDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.context

    @Test fun fixedCorpusProducesBoundedCoversFromOneSnapshotEach() = runBlocking {
        val phase = InstrumentationRegistry.getArguments().getString("phase") ?: "baseline"
        val services = ReadiumServices(context)
        val metadata = ReadiumSourceMetadata(services)
        val access = SourceAccess(context)
        val books = (0 until 12).map { index ->
            coveredEpub(context.cacheDir, "cover-bench-$index.epub", index) to BookFormat.EPUB
        } + (0 until 12).map { index ->
            val name = "cover-bench-$index.pdf"
            val file = if (index == 11) PdfFixtures.scanned(context.cacheDir, name)
                else PdfFixtures.boxed(context.cacheDir, name, pages = 8)
            file to BookFormat.PDF
        }
        // Warm services/code without publishing any corpus covers or reusing a source snapshot.
        access.acquire(BookSource.Owned(books.first().first.path)).use {
            metadata.read(it, BookFormat.EPUB).getOrThrow()
        }
        val started = SystemClock.elapsedRealtimeNanos()
        var firstMs = -1L
        var peakSamplePss = Debug.getPss()
        var copied = 0L
        var stagedNs = 0L
        var extractedNs = 0L
        var writtenNs = 0L
        var epubNs = 0L
        var pdfNs = 0L
        for ((index, pair) in books.withIndex()) {
            val (file, format) = pair
            val source = BookSource.External(
                DocumentsContract.buildDocumentUri("dev.kent.windbook.reader.test.books", file.name).toString(),
                DocumentKey("dev.kent.windbook.reader.test.books", file.name),
            )
            val before = SystemClock.elapsedRealtimeNanos()
            access.acquire(source).use { lease ->
                val sha = lease.fingerprint()
                assertEquals(64, sha.length)
                val staged = SystemClock.elapsedRealtimeNanos()
                val info = metadata.read(lease, format).getOrThrow()
                val extracted = SystemClock.elapsedRealtimeNanos()
                val bytes = requireNotNull(info.coverWebp) { "Missing synthetic cover $index" }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                assertTrue(bounds.outWidth in 1..600 && bounds.outHeight in 1..600)
                val output = File(context.cacheDir, "cover-bench-output.webp")
                output.writeBytes(bytes)
                val written = SystemClock.elapsedRealtimeNanos()
                if (firstMs < 0) firstMs = (written - started) / 1_000_000
                stagedNs += staged - before
                extractedNs += extracted - staged
                writtenNs += written - extracted
                if (format == BookFormat.EPUB) epubNs += extracted - staged
                else pdfNs += extracted - staged
                copied += file.length()
                assertEquals(sha, lease.fingerprint())
                peakSamplePss = maxOf(peakSamplePss, Debug.getPss())
                output.delete()
            }
        }
        val totalMs = (SystemClock.elapsedRealtimeNanos() - started) / 1_000_000
        assertEquals(0, access.workspace.activeLeaseCount)
        Log.i("WBCoverBenchmark", "phase=$phase count=${books.size} bytes=$copied firstMs=$firstMs " +
            "totalMs=$totalMs stageMs=${stagedNs / 1_000_000} extractMs=${extractedNs / 1_000_000} " +
            "writeMs=${writtenNs / 1_000_000} epubMs=${epubNs / 1_000_000} pdfMs=${pdfNs / 1_000_000} " +
            "sampledPeakPssKb=$peakSamplePss")
        Unit
    }

    private fun coveredEpub(dir: File, name: String, index: Int): File {
        val original = TestBooks.epub(dir, "cover-bench-original.epub", title = "Sách thử nghiệm $index", author = "Tác giả")
        val image = Bitmap.createBitmap(1200, 1800, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawColor(Color.rgb(245, 238, 220))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(70, 30 + index * 5, 20); textSize = 80f }
        canvas.drawText("WindBook $index", 50f, 180f, paint)
        repeat(60) { row ->
            paint.color = Color.rgb((row * 31) % 256, (row * 17) % 256, (row * 7) % 256)
            canvas.drawRect(50f, 250f + row * 24, 1150f - row * 3, 260f + row * 24, paint)
        }
        val cover = ByteArrayOutputStream().use { out ->
            image.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        image.recycle()
        val destination = File(dir, name)
        ZipFile(original).use { input ->
            ZipOutputStream(destination.outputStream()).use { output ->
                input.entries().asSequence().forEach { entry ->
                    output.putNextEntry(ZipEntry(entry.name).also { copy ->
                        if (entry.method == ZipEntry.STORED) {
                            copy.method = entry.method; copy.size = entry.size
                            copy.compressedSize = entry.size; copy.crc = entry.crc
                        }
                    })
                    val bytes = input.getInputStream(entry).use { it.readBytes() }
                    output.write(if (entry.name.endsWith(".opf")) {
                        bytes.toString(Charsets.UTF_8).replace("</manifest>",
                            "<item id=\"cover\" href=\"Images/cover.png\" media-type=\"image/png\" properties=\"cover-image\"/></manifest>").toByteArray()
                    } else bytes)
                    output.closeEntry()
                }
                output.putNextEntry(ZipEntry("OEBPS/Images/cover.png"))
                output.write(cover); output.closeEntry()
            }
        }
        return destination
    }
}
