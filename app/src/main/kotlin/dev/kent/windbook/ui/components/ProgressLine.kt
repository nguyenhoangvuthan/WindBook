package dev.kent.windbook.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import dev.kent.windbook.ui.theme.progressFill
import kotlin.math.roundToInt

/** Thin gold reading-progress bar. */
@Composable
fun ProgressLine(progression: Double, modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        progress = { progression.toFloat().coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth().height(3.dp),
        color = MaterialTheme.colorScheme.progressFill,
        trackColor = MaterialTheme.colorScheme.outlineVariant,
        strokeCap = StrokeCap.Round,
        gapSize = 0.dp,
        drawStopIndicator = {},
    )
}

fun formatPercent(progression: Double): String = "${(progression * 100).roundToInt()}%"
