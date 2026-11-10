package dev.kent.windbook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.kent.windbook.core.model.LibraryItem
import dev.kent.windbook.ui.icons.WindBookIcons
import dev.kent.windbook.ui.theme.CoverShape
import java.io.File

private val GeneratedCoverTints = listOf(
    Color(0xFFEFE2C4), Color(0xFFD9E8E2), Color(0xFFF3E7D3),
    Color(0xFFE6D5C3), Color(0xFFDCE3EC), Color(0xFFEAD9DF),
)
internal fun generatedCoverTint(bookId: String): Color =
    GeneratedCoverTints[Math.floorMod(bookId.hashCode(), GeneratedCoverTints.size)]

/** Metadata remains available to screen readers without rendering captions on the shelf. */
internal fun LibraryItem.accessibleBookLabel(): String =
    listOfNotNull(book.title, book.author?.takeIf { it.isNotBlank() }).joinToString(", ")

/** Decorative full cover. Missing/loading/error artwork uses a neutral book outline. */
@Composable
fun BookCover(bookId: String, coverPath: String?, modifier: Modifier = Modifier) {
    val model = remember(coverPath) { coverPath?.let(::File) }
    var loaded by remember(coverPath) { mutableStateOf(false) }
    Box(modifier.aspectRatio(2f / 3f).shadow(3.dp, CoverShape).clip(CoverShape)
        .background(generatedCoverTint(bookId)), contentAlignment = Alignment.Center) {
        if (!loaded) Icon(WindBookIcons.OpenBook, contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .35f), modifier = Modifier.size(28.dp))
        if (model != null) AsyncImage(model = model, contentDescription = null,
            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize(),
            onSuccess = { loaded = true }, onError = { loaded = false })
    }
}
