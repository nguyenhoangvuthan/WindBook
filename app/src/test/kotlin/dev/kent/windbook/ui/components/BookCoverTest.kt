package dev.kent.windbook.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        // The clickable shelf item owns the accessible label; artwork remains decorative.
        // TalkBack reads the merged tree, which is the default one here.
        compose.setContent { BookCover("x", coverPath = null) }
        compose.onNodeWithText("A very long title", substring = true).assertDoesNotExist()
    }

    @Test fun generatedCoverTintsAreAllWarm() {
        // Cool tints clash with the cream paper; warm means red is never below blue.
        assertTrue(GeneratedCoverTints.size >= 6)
        GeneratedCoverTints.forEach { assertTrue("$it is not warm", it.red >= it.blue) }
    }

    @Test fun darkTintsAreDimmedButStillWarmAndStablePerBook() {
        listOf("a", "book-b", "c3", "zz", "42", "Dune").forEach { id ->
            val light = generatedCoverTint(id)
            val dark = generatedCoverTint(id, dark = true)
            assertTrue("$id dark ${dark.luminance()} vs light ${light.luminance()}", dark.luminance() < light.luminance() * 0.6f)
            assertTrue("$id dark tint is not warm", dark.red >= dark.blue)
            assertEquals(dark, generatedCoverTint(id, dark = true))
        }
    }

    @Test fun percentIsRounded() {
        assertEquals("42%", formatPercent(0.4249))
        assertEquals("100%", formatPercent(0.999))
    }

}
