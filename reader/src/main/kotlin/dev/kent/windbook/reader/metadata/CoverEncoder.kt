package dev.kent.windbook.reader.metadata

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/** Borrows the input bitmap; owns and retires only an optional scaled copy. */
internal object CoverEncoder {
    fun encode(bitmap: Bitmap): ByteArray {
        val longest = maxOf(bitmap.width, bitmap.height)
        val scaled = if (longest > 600) {
            val scale = 600.0 / longest
            Bitmap.createScaledBitmap(bitmap, maxOf(1, (bitmap.width * scale).toInt()),
                maxOf(1, (bitmap.height * scale).toInt()), true)
        } else bitmap
        return try {
            ByteArrayOutputStream().use { out ->
                check(scaled.compress(Bitmap.CompressFormat.WEBP_LOSSY, 80, out)) { "Cover encoding failed" }
                out.toByteArray()
            }
        } finally { if (scaled !== bitmap) scaled.recycle() }
    }
}
