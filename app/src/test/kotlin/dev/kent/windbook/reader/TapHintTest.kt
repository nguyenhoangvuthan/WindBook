package dev.kent.windbook.reader

import androidx.compose.ui.test.assertIsDisplayed
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
class TapHintTest {
    @get:Rule val compose = createComposeRule()
    private val text = "Tap the edges to turn pages · tap the middle for the menu"

    @Test fun showsTheZonesAndDismissesOnTheButton() {
        var dismissed = 0
        compose.setContent { TapHint(visible = true, onDismiss = { dismissed++ }) }
        compose.onNodeWithText(text).assertIsDisplayed()
        compose.onNodeWithText("Got it").performClick()
        assertEquals(1, dismissed)
    }

    @Test fun dismissesItselfAfterEightSeconds() {
        var dismissed = 0
        compose.setContent { TapHint(visible = true, onDismiss = { dismissed++ }) }
        compose.mainClock.advanceTimeBy(7_900)
        assertEquals(0, dismissed)
        compose.mainClock.advanceTimeBy(300)
        assertEquals(1, dismissed)
    }

    @Test fun invisibleShowsNothingAndNeverDismisses() {
        var dismissed = 0
        compose.setContent { TapHint(visible = false, onDismiss = { dismissed++ }) }
        compose.mainClock.advanceTimeBy(20_000)
        compose.onNodeWithText(text).assertDoesNotExist()
        assertEquals(0, dismissed)
    }

    @Config(qualifiers = "vi")
    @Test fun vietnameseCopy() {
        compose.setContent { TapHint(visible = true, onDismiss = {}) }
        compose.onNodeWithText("Chạm mép trái/phải để lật trang · chạm giữa để mở menu").assertIsDisplayed()
        compose.onNodeWithText("Đã hiểu").assertIsDisplayed()
    }
}
