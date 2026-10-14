package dev.kent.windbook.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    private val chosen = mutableListOf<AppLanguage>()
    private val state = SettingsUiState(
        language = AppLanguage.SYSTEM, bookCount = 3, totalBytes = 0, freeBytes = 0, versionName = "0.1.0",
    )

    @Test fun currentLanguageIsSelectedAndChoosingCallsBack() {
        compose.setContent { SettingsScreen(state, onLanguageChange = { chosen += it }) }
        compose.onNodeWithText("Automatic (system)").assertIsSelected()
        compose.onNodeWithText("Tiếng Việt").performClick()
        assertEquals(listOf(AppLanguage.VIETNAMESE), chosen)
    }

    @Test fun showsStorageAndVersion() {
        compose.setContent { SettingsScreen(state, onLanguageChange = {}) }
        compose.onNodeWithText("3").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Version 0.1.0").performScrollTo().assertIsDisplayed()
    }

    @Test fun licencesDialogNamesLiterata() {
        compose.setContent { SettingsScreen(state, onLanguageChange = {}) }
        compose.onNodeWithText("Open-source licences").performScrollTo().performClick()
        compose.onNodeWithText("Literata", substring = true).assertIsDisplayed()
    }
}
