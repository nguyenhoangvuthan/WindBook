package dev.kent.windbook.reader.pdf

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.kent.windbook.core.model.AnnotationColor
import dev.kent.windbook.core.model.PdfTextAnnotation
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PdfSelectionActionsTest {
    @get:Rule val compose = createComposeRule()
    private fun ui(ai: Boolean) = PdfAnnotationUi(
        records = emptyList(), selection = null, activeIds = emptyList(), onSelect = {}, onExisting = {}, onNote = {},
        onColor = {}, onDelete = {}, onClear = {}, onNoText = {}, onAi = if (ai) ({}) else null)
    private val saved = PdfTextAnnotation("a", "b", "f", null, "quote", AnnotationColor.GOLD, null, 0, 0)

    @Test fun everyActionIsAButtonOnOneAlignedBar() {
        compose.setContent { PdfSelectionActions(listOf(saved), ui(ai = true)) }
        val labels = listOf("Highlight color", "Note", "AI translate", "Delete")
        val bounds = labels.map { compose.onNodeWithText(it).assertHasClickAction().fetchSemanticsNode().boundsInRoot }
        // Same left edge column or same row: no action may sit on a line of its own with a different inset.
        assertEquals("All actions share one row height", 1, bounds.map { it.height }.distinct().size)
        val rows = bounds.groupBy { it.top }
        assertEquals("Actions wrap only as whole rows", true, rows.values.all { row -> row.map { it.height }.distinct().size == 1 })
        assertEquals("Every row starts at the same left edge", 1, rows.values.map { row -> row.minOf { it.left } }.distinct().size)
    }

    @Test fun aiTranslateIsAbsentWithoutTheAction() {
        compose.setContent { PdfSelectionActions(emptyList(), ui(ai = false)) }
        compose.onNodeWithText("AI translate").assertDoesNotExist()
        compose.onNodeWithText("Delete").assertDoesNotExist()
        compose.onNodeWithText("Note").assertHasClickAction()
    }
}
