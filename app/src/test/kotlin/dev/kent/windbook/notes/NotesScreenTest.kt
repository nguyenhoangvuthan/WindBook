package dev.kent.windbook.notes

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import dev.kent.windbook.core.model.*
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NotesScreenTest {
    @get:Rule val compose=createComposeRule()
    private val record=PdfTextAnnotation("n","b","sha",PdfTextAnchor(page=2,width=400f,height=600f,rects=listOf(PdfTextRect(.1f,.2f,.4f,.3f)),quote="Tiếng Việt"),"Tiếng Việt",AnnotationColor.GOLD,"A saved note",1,2,"Synthetic book")
    @Test fun showsDesignedEmptyState() {
        compose.setContent { NotesScreen() }
        compose.onNodeWithText("Notes").assertIsDisplayed()
        compose.onNodeWithText("Your highlights live here").assertIsDisplayed()
    }
    @org.robolectric.annotation.Config(qualifiers="vi")
    @Test fun vietnamese() {
        compose.setContent { NotesScreen() }
        compose.onNodeWithText("Ghi chú").assertIsDisplayed()
    }
    @Test fun vocabularyButtonAlignsWithTheTitle() {
        compose.setContent { NotesScreen(onVocabulary={}) }
        // TextButton pads its text by 12 dp; a 4 dp start offset puts the text on the title's 16 dp gutter.
        // (Robolectric measures glyphs too narrowly to compare the text itself, so compare the button edge.)
        val title=compose.onNodeWithText("Notes",useUnmergedTree=true).getBoundsInRoot().left
        val button=compose.onNodeWithText("Vocabulary").getBoundsInRoot().left
        assertEquals(title.value,(button+12.dp).value,0.5f)
    }
    @Test fun showsSavedQuoteBodyAndOpensSpecificAnnotation() {
        var opened=""
        compose.setContent { NotesScreen(annotations=listOf(record),onOpen={ opened=it.id }) }
        compose.onNodeWithText("Tiếng Việt").assertIsDisplayed()
        compose.onNodeWithText("A saved note").assertIsDisplayed()
        compose.onNodeWithText("Go to passage").performClick()
        assertEquals("n",opened)
    }
    @Test fun searchMatchesBodyAndQuoteAndHidesUnmatchedRows() {
        compose.setContent { NotesScreen(annotations=listOf(record)) }
        compose.onNodeWithText("Search notes and quotes").performTextInput("missing")
        compose.onNodeWithText("Tiếng Việt").assertDoesNotExist()
        compose.onNodeWithText("Search notes and quotes").performTextClearance()
        compose.onNodeWithText("Search notes and quotes").performTextInput("saved")
        compose.onNodeWithText("Tiếng Việt").assertIsDisplayed()
    }
}
