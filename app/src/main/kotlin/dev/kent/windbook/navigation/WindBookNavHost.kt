package dev.kent.windbook.navigation

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.kent.windbook.library.LibraryRoute
import dev.kent.windbook.notes.NotesScreen
import dev.kent.windbook.reader.ReaderRoute
import dev.kent.windbook.reading.ReadingRoute
import dev.kent.windbook.settings.SettingsRoute

@Composable
fun WindBookNavHost() {
    val backStack = rememberNavBackStack(LibraryKey)
    val currentTab = backStack.lastOrNull()?.topLevelTab()
    Scaffold(
        bottomBar = {
            if (currentTab != null) WindBookTabBar(selected = currentTab, onSelect = { backStack.selectTab(it) })
        },
        // Screens handle their own status-bar inset; the reader must stay edge to edge.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<LibraryKey> {
                    LibraryRoute(onOpenBook = { id -> backStack.openReader(id) })
                }
                entry<ReadingKey> {
                    ReadingRoute(
                        onOpenBook = { id -> backStack.openReader(id) },
                        onBrowseLibrary = { backStack.selectTab(TopLevelTab.LIBRARY) },
                    )
                }
                entry<NotesKey> { NotesScreen() }
                entry<SettingsKey> { SettingsRoute() }
                entry<ReaderKey> { key ->
                    ReaderRoute(bookId = key.bookId, onBack = { backStack.removeLastOrNull() },
                        onReplaceReader = { id ->
                            backStack.removeLastOrNull()
                            backStack.openReader(id)
                        })
                }
            },
        )
    }
}
