package dev.kent.windbook.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Cream paper and bronze gold (reference image 01). Values are pinned by ColorContrastTest. */
internal val LightColors = lightColorScheme(
    primary = Color(0xFF7E5A26),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEBCF9C),
    onPrimaryContainer = Color(0xFF3B2A1A),
    inversePrimary = Color(0xFFD2A464),
    secondary = Color(0xFF7E5A26),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFEBCF9C),
    onSecondaryContainer = Color(0xFF3B2A1A),
    tertiary = Color(0xFFB8894A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEBCF9C),
    onTertiaryContainer = Color(0xFF3B2A1A),
    background = Color(0xFFF7EFDF),
    onBackground = Color(0xFF3B2A1A),
    surface = Color(0xFFF7EFDF),
    onSurface = Color(0xFF3B2A1A),
    surfaceVariant = Color(0xFFF2E8D3),
    onSurfaceVariant = Color(0xFF725E48),
    surfaceTint = Color(0xFFB8894A),
    inverseSurface = Color(0xFF3B2A1A),
    inverseOnSurface = Color(0xFFF7EFDF),
    outline = Color(0xFFA38B6B),
    outlineVariant = Color(0xFFE3D3B4),
    surfaceBright = Color(0xFFFCF7EC),
    surfaceDim = Color(0xFFE8DCC2),
    surfaceContainerLowest = Color(0xFFFFFBF3),
    surfaceContainerLow = Color(0xFFFCF7EC),
    surfaceContainer = Color(0xFFF2E8D3),
    surfaceContainerHigh = Color(0xFFEDE2CB),
    surfaceContainerHighest = Color(0xFFE8DCC2),
)

/** Warm dark: brown-charcoal paper, cream ink, bronze accent. */
internal val DarkColors = darkColorScheme(
    primary = Color(0xFFD2A464),
    onPrimary = Color(0xFF17130F),
    primaryContainer = Color(0xFF5A4426),
    onPrimaryContainer = Color(0xFFEFE4CF),
    inversePrimary = Color(0xFF7E5A26),
    secondary = Color(0xFFD2A464),
    onSecondary = Color(0xFF17130F),
    secondaryContainer = Color(0xFF5A4426),
    onSecondaryContainer = Color(0xFFEFE4CF),
    tertiary = Color(0xFFD2A464),
    onTertiary = Color(0xFF17130F),
    tertiaryContainer = Color(0xFF5A4426),
    onTertiaryContainer = Color(0xFFEFE4CF),
    background = Color(0xFF17130F),
    onBackground = Color(0xFFEFE4CF),
    surface = Color(0xFF17130F),
    onSurface = Color(0xFFEFE4CF),
    surfaceVariant = Color(0xFF2A221A),
    onSurfaceVariant = Color(0xFFA89880),
    surfaceTint = Color(0xFFD2A464),
    inverseSurface = Color(0xFFEFE4CF),
    inverseOnSurface = Color(0xFF17130F),
    outline = Color(0xFF6E5E4A),
    outlineVariant = Color(0xFF3A3026),
    surfaceBright = Color(0xFF342A20),
    surfaceDim = Color(0xFF17130F),
    surfaceContainerLowest = Color(0xFF120F0C),
    surfaceContainerLow = Color(0xFF221C16),
    surfaceContainer = Color(0xFF2A221A),
    surfaceContainerHigh = Color(0xFF342A20),
    surfaceContainerHighest = Color(0xFF3E3227),
)

/** Highlight colours for M4, defined now so the palette stays in one place. */
object HighlightColors {
    val Yellow = Color(0xFFF3D27A)
    val Red = Color(0xFFE58C7A)
    val Green = Color(0xFF8CC9A0)
    val Blue = Color(0xFF82B4E0)
    val Purple = Color(0xFFB49AD8)
    val all = listOf(Yellow, Red, Green, Blue, Purple)
}
