package dev.kent.windbook.core.model

import org.junit.Assert.*
import org.junit.Test

class EpubTextAnnotationTest {
    private val anchor = EpubTextAnchor(href="chapter.xhtml", locatorJson="{}", quote="A sentence", before="Before ", after=" after")
    @Test fun boundedRelativeAnchorIsAccepted() { assertTrue(anchor.isValid()) }
    @Test fun ordinaryEncodedResourceNamesAreAccepted() {
        assertTrue(anchor.copy(href="Text/chapter%201.xhtml").isValid())
        assertTrue(anchor.copy(href="Text/%C4%91o%E1%BA%A1n.xhtml").isValid())
    }
    @Test fun unsafeOrUnboundedAnchorsAreRejected() {
        listOf(anchor.copy(href="https://example.com/chapter"), anchor.copy(href="../chapter"),
            anchor.copy(href="/chapter"), anchor.copy(href="chapter%2F..%2Fsecret"),
            anchor.copy(quote=" "),anchor.copy(quote="x".repeat(4097)),
            anchor.copy(before="x".repeat(513)),anchor.copy(version=2)).forEach { assertFalse(it.isValid()) }
    }
}
