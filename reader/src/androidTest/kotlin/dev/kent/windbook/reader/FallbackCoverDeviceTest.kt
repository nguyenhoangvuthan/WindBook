package dev.kent.windbook.reader

import android.graphics.*
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.reader.metadata.CoverEncoder
import dev.kent.windbook.reader.metadata.epubCoverBitmap
import dev.kent.windbook.testing.TestBooks
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.services.cover
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class FallbackCoverDeviceTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private fun epub(image: ByteArray): File=TestBooks.zip(context.cacheDir,"fallback-cover.epub",mapOf(
        "mimetype" to "application/epub+zip".toByteArray(),
        "META-INF/container.xml" to """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""".toByteArray(),
        "book.opf" to """<package xmlns="http://www.idpf.org/2007/opf" version="2.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">fallback</dc:identifier><dc:title>Fallback fixture</dc:title><dc:creator>Author</dc:creator><dc:contributor>Contributor</dc:contributor><dc:language>vi</dc:language><meta name="cover" content="cover"/></metadata><manifest><item id="cover" href="cover.png" media-type="image/png"/><item id="chapter" href="chapter.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="chapter"/></spine></package>""".toByteArray(),
        "chapter.xhtml" to """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>Chapter</title></head><body><p>Synthetic text</p></body></html>""".toByteArray(),
        "cover.png" to image))
    private fun image(): ByteArray {
        val bitmap=Bitmap.createBitmap(4000,6000,Bitmap.Config.ARGB_8888)
        val c=Canvas(bitmap);val p=Paint()
        listOf(Color.RED,Color.GREEN,Color.BLUE,Color.YELLOW).forEachIndexed {i,color->
            p.color=color;c.drawRect((i%2)*2000f,(i/2)*3000f,(i%2+1)*2000f,(i/2+1)*3000f,p)
        }
        return try {ByteArrayOutputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()}} finally {bitmap.recycle()}
    }
    @Test fun canonicalFallbackDecodesAtShelfSizeAndKeepsMetadata()=runBlocking {
        val file=epub(image());val services=ReadiumServices(context)
        val publication=services.openPublication(file).getOrThrow()
        try {
            val bitmap=requireNotNull(epubCoverBitmap(publication))
            try {
                assertEquals(400,bitmap.width);assertEquals(600,bitmap.height)
                assertTrue(bitmap.allocationByteCount<=600*600*4)
                assertEquals(Color.RED,bitmap.getPixel(20,20))
                assertEquals(Color.YELLOW,bitmap.getPixel(380,580))
            } finally {bitmap.recycle()}
        } finally {publication.close()}
        val metadata=ReadiumMetadataExtractor(services).extract(file,BookFormat.EPUB).getOrThrow()
        assertEquals("Fallback fixture",metadata.title);assertEquals("Author",metadata.author)
        assertNotNull(metadata.coverWebp)
    }
    @Test fun pairedLegacyAndBoundedDecodeObservations()=runBlocking {
        val publication=ReadiumServices(context).openPublication(epub(image())).getOrThrow()
        try {
            repeat(5) {trial->
                for(legacy in listOf(true,false)) {
                    val start=SystemClock.elapsedRealtimeNanos()
                    val bitmap=requireNotNull(if(legacy) publication.cover() else epubCoverBitmap(publication))
                    val allocation=bitmap.allocationByteCount;val pss=Debug.getPss()
                    val encoded=try {CoverEncoder.encode(bitmap)} finally {bitmap.recycle()}
                    val ms=(SystemClock.elapsedRealtimeNanos()-start)/1_000_000
                    Log.i("WBFallbackCover","trial=$trial legacy=$legacy elapsedMs=$ms allocationBytes=$allocation pssKb=$pss outputBytes=${encoded.size}")
                }
            }
        } finally {publication.close()}
    }
    @Test fun oversizedCompressedCoverIsOptionalAndKeepsMetadata()=runBlocking {
        val metadata=ReadiumMetadataExtractor(ReadiumServices(context)).extract(epub(ByteArray(16*1024*1024+1)),BookFormat.EPUB).getOrThrow()
        assertEquals("Fallback fixture",metadata.title);assertNull(metadata.coverWebp)
    }
    @Test fun corruptCoverRemainsOptionalAndDoesNotRejectMetadata()=runBlocking {
        val metadata=ReadiumMetadataExtractor(ReadiumServices(context)).extract(epub("broken".toByteArray()),BookFormat.EPUB).getOrThrow()
        assertEquals("Fallback fixture",metadata.title);assertNull(metadata.coverWebp)
    }
}
