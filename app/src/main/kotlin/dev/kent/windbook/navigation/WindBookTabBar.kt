package dev.kent.windbook.navigation

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import dev.kent.windbook.ui.icons.WindBookIcons

@Composable
fun WindBookTabBar(selected: TopLevelTab, onSelect: (TopLevelTab) -> Unit) {
    val colors = MaterialTheme.colorScheme
    NavigationBar(containerColor = colors.surfaceContainer) {
        TopLevelTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(tab.icon(), contentDescription = null) },
                label = { Text(stringResource(tab.label)) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.onPrimaryContainer,
                    selectedTextColor = colors.primary,
                    indicatorColor = colors.primaryContainer,
                    unselectedIconColor = colors.onSurfaceVariant,
                    unselectedTextColor = colors.onSurfaceVariant,
                ),
            )
        }
    }
}

private fun TopLevelTab.icon(): ImageVector = when (this) {
    TopLevelTab.LIBRARY -> WindBookIcons.Shelf
    TopLevelTab.READING -> WindBookIcons.OpenBook
    TopLevelTab.NOTES -> WindBookIcons.Note
    TopLevelTab.SETTINGS -> WindBookIcons.Settings
}
