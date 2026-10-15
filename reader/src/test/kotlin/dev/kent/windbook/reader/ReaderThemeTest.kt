package dev.kent.windbook.reader

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.preferences.Theme
import org.robolectric.RobolectricTestRunner

// Readium's Theme enum resolves Android colours when it loads, so it needs the Android runtime.
@RunWith(RobolectricTestRunner::class)
class ReaderThemeTest {
    @Test fun lightAppOpensEpubInSepia() = assertEquals(Theme.SEPIA, initialEpubPreferences(darkTheme = false).theme)

    @Test fun darkAppOpensEpubInDark() = assertEquals(Theme.DARK, initialEpubPreferences(darkTheme = true).theme)

    @Test fun epubPageBackgroundMatchesTheTheme() {
        assertEquals(Theme.SEPIA.backgroundColor, epubPageBackground(darkTheme = false))
        assertEquals(Theme.DARK.backgroundColor, epubPageBackground(darkTheme = true))
    }
}
