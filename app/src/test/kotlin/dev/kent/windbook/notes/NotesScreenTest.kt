package dev.kent.windbook.notes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class NotesScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun showsDesignedEmptyState() {
        compose.setContent { NotesScreen() }
        compose.onNodeWithText("Notes").assertIsDisplayed()
        compose.onNodeWithText("Your highlights live here").assertIsDisplayed()
    }

    @Config(qualifiers = "vi")
    @Test fun vietnamese() {
        compose.setContent { NotesScreen() }
        compose.onNodeWithText("Ghi chú").assertIsDisplayed()
    }
}
