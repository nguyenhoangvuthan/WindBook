package dev.kent.windbook.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration

/** Uppercases with the app language's rules (Turkish "i" → "İ"), never Locale.ROOT. */
@Composable
@ReadOnlyComposable
fun String.uppercaseForUi(): String = uppercase(LocalConfiguration.current.locales[0])
