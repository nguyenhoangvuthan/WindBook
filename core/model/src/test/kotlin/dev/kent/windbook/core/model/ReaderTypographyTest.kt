package dev.kent.windbook.core.model

import org.junit.Assert.*
import org.junit.Test

class ReaderTypographyTest {
    @Test fun untouchedBookUsesPublisherAndInheritedTheme() {
        assertEquals(ReaderTypography(), ReaderTypography().resolveOver(ReaderTypography()))
    }
    @Test fun overrideResolvesOnlyChosenFields() {
        val global = ReaderTypography(font=ReaderFont.SERIF, size=130, lineHeight=1.5)
        val book = ReaderTypography(size=160)
        assertEquals(global.copy(size=160), book.resolveOver(global))
    }
    @Test fun invalidAndOutOfRangeValuesAreNormalized() {
        val value = ReaderTypography(size=999, lineHeight=Double.NaN, margin=999).normalized()
        assertEquals(200, value.size)
        assertNull(value.lineHeight)
        assertEquals(2, value.margin)
    }
}
