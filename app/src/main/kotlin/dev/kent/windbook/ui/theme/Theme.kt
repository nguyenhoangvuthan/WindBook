package dev.kent.windbook.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/** App chrome palette (cream & bronze, warm dark). The reading page has its own themes (M2). */
@Composable
fun WindBookTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = WindBookTypography,
        shapes = WindBookShapes,
        content = content,
    )
}
