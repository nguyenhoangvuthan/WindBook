package dev.kent.windbook.reader

import org.junit.Assert.*
import org.junit.Test

class LearningTextTest {
    @Test fun boundedChunksPreserveTextAndOffsets() {
        val text="A sentence. ".repeat(2500)
        val sections=learningChunks("chapter:1",text)
        assertEquals(text,sections.joinToString("") {it.text})
        assertTrue(sections.all {it.text.length<=12000})
        assertEquals("chapter:1#12000",sections[1].location)
    }
    @Test fun oversizedResourceIsNotSilentlyTruncated() {
        assertTrue(runCatching {learningChunks("chapter:1","x".repeat(2097153))}.isFailure)
    }
}
