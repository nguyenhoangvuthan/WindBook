package dev.kent.windbook.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.kent.windbook.R

/** Literata variable font (SIL OFL 1.1). One file, three named weights. */
val Literata = FontFamily(
    Font(R.font.literata, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.literata, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.literata, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

private fun TextStyle.serif(weight: FontWeight) = copy(fontFamily = Literata, fontWeight = weight)

private val Base = Typography()

/** Serif for display/headline/title (screen and book titles); system sans for body and labels. */
val WindBookTypography = Typography(
    displayLarge = Base.displayLarge.serif(FontWeight.Bold),
    displayMedium = Base.displayMedium.serif(FontWeight.Bold),
    displaySmall = Base.displaySmall.serif(FontWeight.Bold),
    headlineLarge = Base.headlineLarge.serif(FontWeight.Bold),
    headlineMedium = Base.headlineMedium.serif(FontWeight.Bold),
    headlineSmall = Base.headlineSmall.serif(FontWeight.Bold),
    titleLarge = Base.titleLarge.serif(FontWeight.SemiBold),
    titleMedium = Base.titleMedium.serif(FontWeight.SemiBold),
    titleSmall = Base.titleSmall.serif(FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge,
    bodyMedium = Base.bodyMedium,
    bodySmall = Base.bodySmall,
    labelLarge = Base.labelLarge,
    labelMedium = Base.labelMedium,
    labelSmall = Base.labelSmall,
)
