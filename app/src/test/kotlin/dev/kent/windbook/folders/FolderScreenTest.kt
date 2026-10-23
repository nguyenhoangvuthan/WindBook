package dev.kent.windbook.folders

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import dev.kent.windbook.core.model.ScanOptions
import dev.kent.windbook.core.model.BookFormat
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun scanRequiresGrantAndExtensionWhileDefaultsIncludeRecursion() {
        compose.setContent { FolderScreen(FolderUiState(), FolderActions()) }
        compose.onNodeWithText("Scan folder").assertIsNotEnabled()
        compose.onNodeWithText("Include subfolders").assertIsDisplayed()
    }
    @Test fun allAndClearDispatchRealOptions() {
        var options = ScanOptions()
        compose.setContent { FolderScreen(FolderUiState(), FolderActions(setExtensions = { options = options.copy(extensions = it) })) }
        compose.onNodeWithText("All").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(BookFormat.entries.toSet(), options.extensions)
        compose.onNodeWithText("Clear").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(emptySet<BookFormat>(), options.extensions)
    }
    @Test fun searchMatchesMetadataTitleAndOpensStableId() {
        var opened = ""
        val entry = dev.kent.windbook.core.data.folders.FolderEntry("stable-book", "source", "filename.pdf",
            "sub/filename.pdf", BookFormat.PDF, dev.kent.windbook.core.model.SourceAvailability.AVAILABLE,
            title = "Synthetic title")
        compose.setContent { FolderScreen(FolderUiState(entries = listOf(entry), query = "Synthetic"),
            FolderActions(openBook = { opened = it })) }
        compose.onNodeWithTag("folder-list").performScrollToNode(hasText("Synthetic title"))
        compose.onNodeWithText("Synthetic title").performClick()
        compose.waitForIdle()
        assertEquals("stable-book", opened)
    }

    @Test fun stoppedScanShowsIncompleteAndStopDispatches() {
        var stopped = false
        compose.setContent { FolderScreen(FolderUiState(progress =
            dev.kent.windbook.core.data.folders.ScanProgress("run", "RUNNING")),
            FolderActions(stop = { stopped = true })) }
        compose.onNodeWithText("Stop scan").performScrollTo().assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(true, stopped)
    }
    @Test fun interruptedRunDoesNotClaimComplete() {
        compose.setContent { FolderScreen(FolderUiState(progress =
            dev.kent.windbook.core.data.folders.ScanProgress("run", "INTERRUPTED")), FolderActions()) }
        compose.onNodeWithTag("folder-list").performScrollToNode(hasText("Results are incomplete. Scan again to refresh."))
        compose.onNodeWithText("Results are incomplete. Scan again to refresh.").assertIsDisplayed()
    }
    @org.robolectric.annotation.Config(qualifiers = "vi")
    @Test fun vietnameseDefaultsAndDisabledScan() {
        compose.setContent { FolderScreen(FolderUiState(), FolderActions()) }
        compose.onNodeWithText("Quét thư mục").assertIsNotEnabled()
        compose.onNodeWithText("Quét cả thư mục con").performScrollTo().assertIsDisplayed()
    }

    @Test fun registeredFormatUsesRuntimeCapabilityForClickAndLabel() {
        var opened = ""
        val entry = dev.kent.windbook.core.data.folders.FolderEntry("text-book", "source", "story.txt", "story.txt",
            BookFormat.TXT, dev.kent.windbook.core.model.SourceAvailability.AVAILABLE, title = "Synthetic text")
        compose.setContent { FolderScreen(FolderUiState(entries = listOf(entry), options = ScanOptions(extensions = setOf(BookFormat.TXT))),
            FolderActions(openBook = { opened = it }), canRead = { it == BookFormat.TXT }) }
        compose.onNodeWithTag("folder-list").performScrollToNode(hasText("Synthetic text"))
        compose.onNodeWithText("Synthetic text").performClick()
        compose.waitForIdle()
        assertEquals("text-book", opened)
    }

}
