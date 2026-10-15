package dev.kent.windbook.reader.pdf

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.File

/** Real, renderable PDFs written with the framework PdfDocument. */
object PdfFixtures {
    /**
     * [pages] pages of [pageW]×[pageH] points with a solid black box from ([boxL],[boxT]) to ([boxR],[boxB]).
     * Odd pages (1, 3, …) are shifted right by [mirrorShift] points to imitate printed margins.
     */
    fun boxed(
        dir: File,
        name: String = "boxed.pdf",
        pages: Int = 10,
        pageW: Int = 400,
        pageH: Int = 600,
        boxL: Float = 100f, boxT: Float = 150f, boxR: Float = 300f, boxB: Float = 450f,
        mirrorShift: Float = 0f,
    ): File {
        val doc = PdfDocument()
        val paint = Paint().apply { color = Color.BLACK }
        repeat(pages) { i ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, i + 1).create())
            val shift = if (i % 2 == 0) mirrorShift else 0f
            page.canvas.drawRect(boxL + shift, boxT, boxR + shift, boxB, paint)
            doc.finishPage(page)
        }
        val file = File(dir, name)
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }
}
