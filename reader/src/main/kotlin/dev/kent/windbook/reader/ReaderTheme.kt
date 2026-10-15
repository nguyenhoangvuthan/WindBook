package dev.kent.windbook.reader

import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.Theme

/** Until M2 adds reading themes, the EPUB page follows the app: sepia paper in light mode, dark at night. */
internal fun initialEpubPreferences(darkTheme: Boolean): EpubPreferences =
    EpubPreferences(theme = if (darkTheme) Theme.DARK else Theme.SEPIA)

/** Colour of the EPUB page, used to paint the spacing around it so the edges blend in. */
fun epubPageBackground(darkTheme: Boolean): Int = (if (darkTheme) Theme.DARK else Theme.SEPIA).backgroundColor
