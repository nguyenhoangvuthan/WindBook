package dev.kent.windbook.reader.metadata

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import java.nio.ByteBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.firstWithRel
import org.readium.r2.shared.publication.services.cover

/** Targets the shelf size before allocating pixels, including EXIF orientation. */
internal fun decodeCoverRaster(encoded: ByteArray): Bitmap {
    require(encoded.size <= MAX_COVER_BYTES)
    return ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(encoded))) { decoder, info, _ ->
        val width=info.size.width;val height=info.size.height
        require(width>0 && height>0)
        val scale=minOf(1.0,600.0/maxOf(width,height))
        decoder.allocator=ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.setTargetSize(maxOf(1,(width*scale).toInt()),maxOf(1,(height*scale).toInt()))
    }
}

/** Uses Readium's canonical cover relation without asking its service for a full-size bitmap. */
internal suspend fun epubCoverBitmap(publication: Publication): Bitmap? {
    val link=(publication.resources+publication.readingOrder).firstWithRel("cover")
        ?: return publication.cover()
    return try {
        val resource=publication.get(link) ?: return null
        val encoded=try {
            val length=resource.length().getOrNull()
            if(length!=null && length>MAX_COVER_BYTES) return null
            resource.read(0L..MAX_COVER_BYTES.toLong()).getOrNull()?.takeIf {it.size<=MAX_COVER_BYTES}
        } finally {resource.close()}
        currentCoroutineContext().ensureActive()
        encoded?.let(::decodeCoverRaster)
    } catch(e: CancellationException) {throw e}
    catch(_: Exception) {currentCoroutineContext().ensureActive();null}
}

private const val MAX_COVER_BYTES=16*1024*1024
