package dev.kent.windbook.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.kent.windbook.ui.theme.CoverShape
import java.io.File

private val GeneratedCoverTints = listOf(
    Color(0xFFEFE2C4), Color(0xFFD9E8E2), Color(0xFFF3E7D3),
    Color(0xFFE6D5C3), Color(0xFFDCE3EC), Color(0xFFEAD9DF),
)
private val GeneratedCoverInk = Color(0xFF3B2A1A)

/** A warm paper tint for books without a cover image, stable for a given book. */
internal fun generatedCoverTint(bookId: String): Color =
    GeneratedCoverTints[Math.floorMod(bookId.hashCode(), GeneratedCoverTints.size)]

/** Wide covers (slides, landscape PDFs) are shown whole; book-shaped covers fill the 2:3 frame. */
internal fun coverScaleFor(width: Float, height: Float): ContentScale =
    if (width > height) ContentScale.Fit else ContentScale.Crop

/**
 * 2:3 book cover with a soft shadow. Decorative: callers show the title as text next to it.
 * [titleEndInset] keeps a generated cover's title clear of a button overlaid on its top-end corner.
 */
@Composable
fun BookCover(bookId: String, title: String, coverPath: String?, modifier: Modifier = Modifier, titleEndInset: Dp = 0.dp) {
    val shaped = modifier.aspectRatio(2f / 3f).shadow(4.dp, CoverShape).clip(CoverShape)
    if (coverPath != null) {
        var scale by remember(coverPath) { mutableStateOf(ContentScale.Crop) }
        AsyncImage(
            model = File(coverPath),
            contentDescription = null,
            contentScale = scale,
            onSuccess = { state -> state.painter.intrinsicSize.let { scale = coverScaleFor(it.width, it.height) } },
            modifier = shaped.background(generatedCoverTint(bookId)),
        )
    } else {
        Box(shaped.background(generatedCoverTint(bookId)).padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 8.dp + titleEndInset).clearAndSetSemantics {}) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = GeneratedCoverInk,
                maxLines = 5,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
