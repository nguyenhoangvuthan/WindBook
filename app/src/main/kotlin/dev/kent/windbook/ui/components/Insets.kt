package dev.kent.windbook.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable

/**
 * Safe area for tab screens: the status bar on top, plus the camera cutout and a side navigation
 * bar in landscape. The bottom is owned by the tab bar.
 */
val TabScreenInsets: WindowInsets
    @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
