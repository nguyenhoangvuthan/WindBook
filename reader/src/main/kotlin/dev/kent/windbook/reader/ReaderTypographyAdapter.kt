package dev.kent.windbook.reader

import dev.kent.windbook.core.model.*
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.Theme

internal fun readingTheme(value: ReaderTypography,darkTheme: Boolean): Theme = when(value.colors) {
    ReaderColors.PAPER -> Theme.LIGHT
    ReaderColors.SEPIA -> Theme.SEPIA
    ReaderColors.DARK -> Theme.DARK
    ReaderColors.SYSTEM,null -> if(darkTheme) Theme.DARK else Theme.SEPIA
}

internal fun typographyPreferences(value: ReaderTypography,darkTheme: Boolean): EpubPreferences {
    val clean=value.normalized()
    return EpubPreferences(
        theme=readingTheme(clean,darkTheme),
        fontFamily=when(clean.font) {
            ReaderFont.LITERATA -> FontFamily("Literata")
            ReaderFont.SERIF -> FontFamily.SERIF
            ReaderFont.SANS -> FontFamily.SANS_SERIF
            ReaderFont.PUBLISHER,null -> null
        },
        fontSize=clean.size?.let {it / 100.0},
        lineHeight=clean.lineHeight?.takeIf {it>0},
        pageMargins=clean.margin?.let {listOf(.5,1.0,1.5)[it]},
    )
}

fun readerPageBackground(value: ReaderTypography,darkTheme: Boolean): Int = readingTheme(value,darkTheme).backgroundColor
