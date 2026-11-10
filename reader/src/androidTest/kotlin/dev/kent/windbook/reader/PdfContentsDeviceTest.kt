package dev.kent.windbook.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.kent.windbook.core.model.*
import dev.kent.windbook.reader.metadata.PdfContentsExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfContentsDeviceTest {
    private val context=InstrumentationRegistry.getInstrumentation().context
    @Test fun metadataOutlineGoesToExactFrameworkPagesAndNeverChangesReadingEngine()=runBlocking {
        val file=fixture("outline.pdf",true)
        val services=ReadiumServices(context)
        val contents=PdfContentsExtractor(services).read(file,3)
        assertEquals(ContentsStatus.READY,contents.status)
        assertEquals(1,contents.entries[0].page)
        assertEquals(3,contents.entries[0].children[0].page)
        val session=ReaderEngine(services).open(file,BookFormat.PDF).getOrThrow() as PdfSession
        try {
            assertEquals(NavigationSurface.PDF,session.surface)
            for(page in listOf(1,3)) {
                val locator=session.locatorJsonFor(page-1)
                assertEquals(page-1,session.pageOf(locator))
                val bitmap=session.pages.render(session.pageOf(locator),CropBox.FULL,200)
                bitmap.recycle()
            }
        } finally {session.close()}
        val imported=ReadiumMetadataExtractor(services).extract(file,BookFormat.PDF).getOrThrow()
        assertEquals(contents,imported.pdfContents)
        assertNotNull(imported.coverWebp)
    }
    @Test fun pdfWithoutOutlineIsKnownEmpty()=runBlocking {
        val contents=PdfContentsExtractor(ReadiumServices(context)).read(fixture("empty.pdf",false),3)
        assertEquals(ContentsStatus.EMPTY,contents.status)
    }
    private fun fixture(name: String,outline: Boolean): File {
        val objects=listOf(
            "<< /Type /Catalog /Pages 2 0 R ${if(outline) "/Outlines 3 0 R" else ""} >>",
            "<< /Type /Pages /Kids [6 0 R 7 0 R 8 0 R] /Count 3 >>",
            "<< /Type /Outlines /First 4 0 R /Last 4 0 R /Count 2 >>",
            "<< /Title (First) /Parent 3 0 R /Dest [6 0 R /Fit] /First 5 0 R /Last 5 0 R /Count 1 >>",
            "<< /Title (Last) /Parent 4 0 R /Dest [8 0 R /Fit] >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 400 600] /Resources << >> /Contents 9 0 R >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 400 600] /Resources << >> /Contents 9 0 R >>",
            "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 400 600] /Resources << >> /Contents 9 0 R >>",
            "<< /Length 0 >>\nstream\n\nendstream")
        val out=ByteArrayOutputStream();fun write(s: String) {out.write(s.toByteArray(Charsets.US_ASCII))}
        write("%PDF-1.4\n");val offsets=mutableListOf<Int>()
        objects.forEachIndexed {i,obj ->offsets+=out.size();write("${i+1} 0 obj\n$obj\nendobj\n")}
        val xref=out.size();write("xref\n0 ${objects.size+1}\n0000000000 65535 f \n")
        offsets.forEach {write("%010d 00000 n \n".format(java.util.Locale.ROOT,it))}
        write("trailer\n<< /Size ${objects.size+1} /Root 1 0 R >>\nstartxref\n$xref\n%%EOF\n")
        return File(context.cacheDir,name).apply {writeBytes(out.toByteArray())}
    }
}
