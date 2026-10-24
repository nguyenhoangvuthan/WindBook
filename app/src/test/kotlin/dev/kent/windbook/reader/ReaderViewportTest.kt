package dev.kent.windbook.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ReaderViewportTest {
    @get:Rule val compose = createComposeRule()

    @Test fun readerAndNestedControlsRespectChangingInsetsWithoutDoublePadding() {
        val insets = mutableStateOf(WindowInsets(left = 24, top = 72, right = 36, bottom = 18))
        compose.setContent {
            Box(Modifier.size(200.dp, 300.dp).testTag("screen")) {
                ReaderViewport(insets = insets.value) {
                    // Reader controls also request insets; the parent must consume them.
                    Box(Modifier.fillMaxSize().windowInsetsPadding(insets.value).testTag("book"))
                }
            }
        }
        assertBounds(24f, 72f, 36f, 18f)
        compose.runOnIdle { insets.value = WindowInsets(left = 72, top = 24, right = 0, bottom = 18) }
        assertBounds(72f, 24f, 0f, 18f)
    }

    private fun assertBounds(left: Float, top: Float, right: Float, bottom: Float) {
        val screen = compose.onNodeWithTag("screen").fetchSemanticsNode().boundsInRoot
        val book = compose.onNodeWithTag("book").fetchSemanticsNode().boundsInRoot
        assertEquals(screen.left + left, book.left, 0.1f)
        assertEquals(screen.top + top, book.top, 0.1f)
        assertEquals(screen.right - right, book.right, 0.1f)
        assertEquals(screen.bottom - bottom, book.bottom, 0.1f)
    }
}
