package dev.kent.windbook.reader

import dev.kent.windbook.core.model.*
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.preferences.FontFamily
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme

internal fun readingTheme(value: ReaderTypography,darkTheme: Boolean): Theme = when(value.colors) {
    ReaderColors.PAPER -> Theme.LIGHT
    ReaderColors.SEPIA -> Theme.SEPIA
    ReaderColors.DARK -> Theme.DARK
    ReaderColors.SYSTEM,null -> if(darkTheme) Theme.DARK else Theme.SEPIA
}

internal fun typographyPreferences(value: ReaderTypography,darkTheme: Boolean): EpubPreferences {
    val clean=value.normalized()
    // Clean layout (the default) turns publisher styles off so the app's spacing, alignment and
    // line height apply; "keep original formatting" leaves the book's own styles in charge.
    val appLayout=clean.originalLayout!=true
    return EpubPreferences(
        theme=readingTheme(clean,darkTheme),
        fontFamily=when(clean.font) {
            ReaderFont.LITERATA -> FontFamily("Literata")
            ReaderFont.SERIF -> FontFamily.SERIF
            ReaderFont.SANS -> FontFamily.SANS_SERIF
            ReaderFont.PUBLISHER,null -> null
        },
        fontSize=clean.size?.let {it / 100.0},
        lineHeight=clean.lineHeight?.takeIf {it>0} ?: if(appLayout) DEFAULT_LINE_HEIGHT else null,
        pageMargins=clean.margin?.let {listOf(.5,1.0,1.5)[it]},
        publisherStyles=if(appLayout) false else null,
        textAlign=if(appLayout) TextAlign.JUSTIFY else null,
        paragraphSpacing=if(appLayout) PARAGRAPH_SPACING else null,
        paragraphIndent=if(appLayout) 0.0 else null,
        hyphens=if(appLayout) true else null,
    )
}

private const val DEFAULT_LINE_HEIGHT=1.5
private const val PARAGRAPH_SPACING=0.5

fun readerPageBackground(value: ReaderTypography,darkTheme: Boolean): Int = readingTheme(value,darkTheme).backgroundColor
