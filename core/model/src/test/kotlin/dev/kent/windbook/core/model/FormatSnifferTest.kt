package dev.kent.windbook.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatSnifferTest {
    @Test fun pdfMagicAtStart() =
        assertEquals(FileSignature.PDF, FormatSniffer.signature("%PDF-1.7\n".toByteArray()))

    @Test fun pdfMagicAfterJunkWithinHeader() =
        assertEquals(FileSignature.PDF, FormatSniffer.signature(ByteArray(300) { 0x20 } + "%PDF-1.4".toByteArray()))

    @Test fun zipMagic() =
        assertEquals(FileSignature.ZIP, FormatSniffer.signature(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00)))

    @Test fun emptyIsUnknown() =
        assertEquals(FileSignature.UNKNOWN, FormatSniffer.signature(ByteArray(0)))

    @Test fun textIsUnknown() =
        assertEquals(FileSignature.UNKNOWN, FormatSniffer.signature("hello world".toByteArray()))
}
