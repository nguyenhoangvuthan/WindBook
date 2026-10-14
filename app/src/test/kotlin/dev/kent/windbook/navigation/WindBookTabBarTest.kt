package dev.kent.windbook.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class WindBookTabBarTest {
    @get:Rule val compose = createComposeRule()

    @Test fun showsFourTabsAndReportsSelection() {
        val picked = mutableListOf<TopLevelTab>()
        compose.setContent { WindBookTabBar(selected = TopLevelTab.LIBRARY, onSelect = { picked += it }) }
        compose.onNodeWithText("Library").assertIsSelected()
        compose.onNodeWithText("Reading").assertIsDisplayed()
        compose.onNodeWithText("Notes").assertIsDisplayed()
        compose.onNodeWithText("Settings").performClick()
        assertEquals(listOf(TopLevelTab.SETTINGS), picked)
    }

    @Config(qualifiers = "vi")
    @Test fun vietnameseLabels() {
        compose.setContent { WindBookTabBar(selected = TopLevelTab.LIBRARY, onSelect = {}) }
        compose.onNodeWithText("Kệ sách").assertIsDisplayed()
        compose.onNodeWithText("Cài đặt").assertIsDisplayed()
    }
}
