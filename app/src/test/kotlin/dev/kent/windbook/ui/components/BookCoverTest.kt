package dev.kent.windbook.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BookCoverTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tintIsStablePerBook() {
        assertEquals(generatedCoverTint("book-a"), generatedCoverTint("book-a"))
    }

    @Test fun generatedCoverTitleIsDecorativeForAccessibility() {
        // The title under the cover is what TalkBack reads; the cover's own copy must not duplicate it.
        // TalkBack reads the merged tree, which is the default one here.
        compose.setContent { BookCover("x", "A very long title that goes on and on and on", coverPath = null) }
        compose.onNodeWithText("A very long title", substring = true).assertDoesNotExist()
    }

    @Test fun percentIsRounded() {
        assertEquals("42%", formatPercent(0.4249))
        assertEquals("100%", formatPercent(0.999))
    }
}
