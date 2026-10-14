package dev.kent.windbook.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.kent.windbook.ui.theme.PillShape

/**
 * Rounded pill (reference images 01 and 02).
 * [selected] null = a plain action button (TalkBack: "button"); true/false = a toggle such as a filter
 * (TalkBack: "selected"/"not selected"). Selected pills use the gold container.
 */
@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    leadingIcon: ImageVector? = null,
) {
    val colors = MaterialTheme.colorScheme
    val on = selected == true
    val shapeModifier = modifier.minimumInteractiveComponentSize().heightIn(min = 36.dp)
    val color = if (on) colors.primaryContainer else colors.surfaceContainerLow
    val contentColor = if (on) colors.onPrimaryContainer else colors.onSurface
    val border = if (on) null else BorderStroke(1.dp, colors.outlineVariant)
    val content: @Composable () -> Unit = { PillContent(text, on, leadingIcon) }
    if (selected == null) {
        Surface(
            onClick = onClick,
            modifier = shapeModifier.semantics { role = Role.Button },
            shape = PillShape,
            color = color,
            contentColor = contentColor,
            border = border,
            content = content,
        )
    } else {
        Surface(
            selected = selected,
            onClick = onClick,
            modifier = shapeModifier,
            shape = PillShape,
            color = color,
            contentColor = contentColor,
            border = border,
            content = content,
        )
    }
}

@Composable
private fun PillContent(text: String, selected: Boolean, leadingIcon: ImageVector?) {
    Row(
        Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        leadingIcon?.let { Icon(it, contentDescription = null, modifier = Modifier.size(18.dp)) }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
