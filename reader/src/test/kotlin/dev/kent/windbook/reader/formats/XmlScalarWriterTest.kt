package dev.kent.windbook.reader.formats

import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test

class XmlScalarWriterTest {
    @Test fun referencesSplitAtEveryBoundaryPreserveCodepointsAndLiteralEscapedText() {
        val input = "<p>&amp;#55357;&amp;#56832; &#65;&#55357;&#56832;&#55357;&#56832;</p>"
        for (chunk in 1..input.length) {
            val target = StringWriter()
            val writer = XmlScalarWriter(target)
            input.chunked(chunk).forEach { writer.write(it) }
            writer.finish()
            assertEquals(target.toString().length.toLong(), writer.units)
            val dom = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(target.toString().byteInputStream())
            assertEquals("&#55357;&#56832; A😀😀", dom.documentElement.textContent)
        }
    }
    @Test fun actualRepairedUnitsAreBoundedAndLoneSurrogatesRejected() {
        val output = StringWriter()
        val writer = XmlScalarWriter(output, 9)
        writer.write("&#55357;&#56832;")
        writer.finish()
        assertEquals("&#128512;", output.toString())
        assertEquals(9L, writer.units)
        assertThrows(IllegalArgumentException::class.java) { writer.write("x") }
        val lone = XmlScalarWriter(StringWriter())
        lone.write("&#55357;")
        assertThrows(IllegalArgumentException::class.java) { lone.finish() }
    }
}
