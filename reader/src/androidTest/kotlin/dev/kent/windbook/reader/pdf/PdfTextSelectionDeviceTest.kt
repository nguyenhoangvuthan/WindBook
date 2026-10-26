package dev.kent.windbook.reader.pdf

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.models.selection.SelectionBoundary
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfTextSelectionDeviceTest {
    @Test fun frameworkSelectsVietnameseWordMultilineAndEmptySpace() {
        assumeTrue(Build.VERSION.SDK_INT >= 35)
        val file = fixture()
        PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)).use { renderer ->
            renderer.openPage(0).use { page ->
                fun select(x: Int, y: Int, endX: Int = x, endY: Int = y) = page.selectContent(
                    SelectionBoundary(Point(x, y)), SelectionBoundary(Point(endX, endY)),
                )
                val word = requireNotNull(select(55, 93))
                val quote = word.selectedTextContents.joinToString("") { it.text }
                Log.i("PdfTextGate", "word=$quote bounds=${word.selectedTextContents.flatMap { it.bounds }}")
                assertEquals("Tiếng", quote.trim())
                assertTrue(word.selectedTextContents.flatMap { it.bounds }.all { it.left >= 39f && it.top > 75f && it.bottom <= 105f })
                val lines = requireNotNull(select(42, 93, 240, 133))
                val text = lines.selectedTextContents.joinToString("") { it.text }
                val reverse = requireNotNull(select(240, 133, 42, 93)).selectedTextContents.joinToString("") { it.text }
                Log.i("PdfTextGate", "multiline=$text reverse=$reverse bounds=${lines.selectedTextContents.flatMap { it.bounds }}")
                assertTrue(text.contains("Tiếng Việt"))
                assertTrue(text.contains("Second line"))
                assertEquals(text, reverse)
                assertTrue(lines.selectedTextContents.flatMap { it.bounds }.size >= 2)
                assertNull(select(390, 580))
            }
        }
    }

    @Test fun documentSelectionUsesFullPagePointsAndClosesPageBeforeRendering() = kotlinx.coroutines.runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= 35)
        PdfDocumentHandle.open(fixture()).use { document ->
            val selected = requireNotNull(document.selectText(0, dev.kent.windbook.core.model.PdfPoint(55f,93f), dev.kent.windbook.core.model.PdfPoint(55f,93f)))
            assertEquals("Tiếng", selected.anchor.quote.trim())
            assertEquals(0.1f, selected.anchor.rects.first().left, 0.001f)
            assertEquals(400f, selected.anchor.width, 0.001f)
            val bitmap = document.render(0, dev.kent.windbook.core.model.CropBox.FULL, 400)
            assertEquals(400, bitmap.width)
            bitmap.recycle()
        }
    }

    @Test fun sessionFingerprintComesFromThePinnedSourceBytes() = kotlinx.coroutines.runBlocking {
        val access=dev.kent.windbook.reader.source.SourceAccess(InstrumentationRegistry.getInstrumentation().targetContext)
        val lease=access.acquire(dev.kent.windbook.core.model.BookSource.Owned(fixture().path))
        val expected=lease.fingerprint()
        val session=PdfReaderSession(PdfDocumentHandle.open(lease.openDescriptor()),lease)
        try { assertEquals(expected,session.annotationFingerprint()) } finally { session.close() }
    }

    companion object { private var exported=false }

    private fun fixture(): java.io.File {
        val file = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.resolve("selection-synthetic.pdf")
        val document = PdfDocument()
        try {
            val paint = Paint().apply { color = Color.BLACK; textSize = 20f; isAntiAlias = true }
            repeat(3) { index ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(if(index==1) 600 else 400,if(index==1) 400 else 600,index+1).create())
                if(index==2) page.canvas.rotate(90f,200f,300f)
                page.canvas.drawText("Tiếng Việt đọc sách", 40f, 100f, paint)
                page.canvas.drawText("Second line selection", 40f, 140f, paint)
                page.canvas.drawText("Page ${index+1}",40f,200f,paint)
                document.finishPage(page)
            }
            file.outputStream().use(document::writeTo)
        } finally { document.close() }
        if(!exported) {
            val context=InstrumentationRegistry.getInstrumentation().targetContext
            val values=android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME,"PdfNotesGate.pdf")
                put(android.provider.MediaStore.Downloads.MIME_TYPE,"application/pdf")
                put(android.provider.MediaStore.Downloads.RELATIVE_PATH,android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val uri=requireNotNull(context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,values))
            requireNotNull(context.contentResolver.openOutputStream(uri)).use { out -> file.inputStream().use { it.copyTo(out) } }
            Log.i("PdfTextGate","Exported synthetic three-page fixture: $uri")
            exported=true
        }
        return file
    }
}
