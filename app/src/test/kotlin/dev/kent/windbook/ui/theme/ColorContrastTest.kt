package dev.kent.windbook.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorContrastTest {

    private fun ratio(a: Color, b: Color): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun ColorScheme.textPairs(): List<Triple<String, Color, Color>> = listOf(
        Triple("onSurface/surface", onSurface, surface),
        Triple("onSurface/surfaceContainerLow", onSurface, surfaceContainerLow),
        Triple("onSurface/surfaceContainer", onSurface, surfaceContainer),
        Triple("onSurface/surfaceContainerHigh", onSurface, surfaceContainerHigh),
        Triple("onSurfaceVariant/surface", onSurfaceVariant, surface),
        Triple("onSurfaceVariant/surfaceContainerLow", onSurfaceVariant, surfaceContainerLow),
        Triple("onSurfaceVariant/surfaceContainer", onSurfaceVariant, surfaceContainer),
        Triple("onSurfaceVariant/surfaceContainerHigh", onSurfaceVariant, surfaceContainerHigh),
        Triple("primary/surface", primary, surface),
        Triple("primary/surfaceContainerLow", primary, surfaceContainerLow),
        Triple("primary/surfaceContainer", primary, surfaceContainer),
        Triple("onPrimary/primary", onPrimary, primary),
        Triple("onPrimaryContainer/primaryContainer", onPrimaryContainer, primaryContainer),
        Triple("onSecondaryContainer/secondaryContainer", onSecondaryContainer, secondaryContainer),
    )

    private fun assertScheme(name: String, scheme: ColorScheme) {
        scheme.textPairs().forEach { (pair, fg, bg) ->
            val r = ratio(fg, bg)
            assertTrue("$name $pair is ${"%.2f".format(r)}:1, needs 4.5:1", r >= 4.5)
        }
        val fab = ratio(scheme.onTertiary, scheme.tertiary)
        assertTrue("$name FAB icon is ${"%.2f".format(fab)}:1, needs 3:1", fab >= 3.0)
    }

    @Test fun lightSchemeMeetsContrast() = assertScheme("light", LightColors)

    @Test fun darkSchemeMeetsContrast() = assertScheme("dark", DarkColors)

    @Test fun highlightColoursKeepDarkInkReadable() {
        val ink = LightColors.onSurface
        HighlightColors.all.forEach { assertTrue(ratio(ink, it) >= 4.5) }
    }
}
