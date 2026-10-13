package dev.kent.windbook.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextTest {
    @Test fun stripsVietnameseDiacriticsAndCase() =
        assertEquals("tieng viet", SearchText.normalize("Tiếng Việt"))

    @Test fun mapsDStroke() =
        assertTrue(SearchText.normalize("Đắc Nhân Tâm").contains("dac nhan"))

    @Test fun plainAsciiUnchangedExceptCase() =
        assertEquals("clean code", SearchText.normalize("Clean Code"))
}
