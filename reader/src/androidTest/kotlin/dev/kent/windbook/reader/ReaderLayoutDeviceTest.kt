package dev.kent.windbook.reader

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.lifecycleScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.kent.windbook.core.model.BookFormat
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.epub.EpubNavigatorFragment

/** Long code lines must wrap inside the page column instead of spilling onto the next page. */
@RunWith(AndroidJUnit4::class)
class ReaderLayoutDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun codeListingLaidOutAsATableStaysInsideTheColumn() {
        val session = runBlocking { ReaderEngine(ReadiumServices(context)).open(codeTableEpub(), BookFormat.EPUB).getOrThrow() }
        val scenario = launchFragmentInContainer<ReaderHostFragment>(
            fragmentArgs = ReaderHostFragment.arguments(NavigationSurface.PUBLICATION),
            themeResId = androidx.appcompat.R.style.Theme_AppCompat_DayNight_NoActionBar,
        )
        try {
            val seen = CountDownLatch(1)
            scenario.onFragment { it.bind(session, null, object : ReaderHostFragment.Callbacks {
                override fun onLocatorChanged(locatorJson: String, progression: Double, chapterTitle: String?) = seen.countDown()
                override fun onCenterTap() = Unit
            }, false) }
            assertTrue(seen.await(20, TimeUnit.SECONDS))
            Thread.sleep(1500)
            val result = AtomicReference<String?>()
            val done = CountDownLatch(1)
            scenario.onFragment { fragment -> fragment.lifecycleScope.launch {
                val navigator = fragment.childFragmentManager.fragments.filterIsInstance<EpubNavigatorFragment>().single()
                result.set(navigator.evaluateJavascript("""(function(){
                    const code=document.querySelector('.ProgramCode').getBoundingClientRect();
                    const text=document.querySelector('p').getBoundingClientRect();
                    return Math.round(code.right-text.right)<=1;})()"""))
                done.countDown()
            } }
            assertTrue(done.await(10, TimeUnit.SECONDS))
            assertEquals("the code listing is wider than the text column", "true", result.get())
        } finally { scenario.close(); session.close() }
    }

    /** Springer-style listing: a table of line divs whose own rule asks for `word-wrap: break-word`. */
    private fun codeTableEpub(): File {
        val css = ".ProgramCode{display:table}.ProgramCode .FixedLine{font-family:monospace;word-wrap:break-word}"
        val code = listOf(
            "data azurerm_storage_account_sas state_container_sas_token {",
            "  connection_string = azurerm_storage_account.statestorage.primary_connection_string",
            "  sas = \"?sv=2017-07-29&amp;ss=b&amp;srt=sco&amp;sp=rwdlacup&amp;se=2021-10-20T00:00:00Z&amp;sig=2FxMCLxnEa5jpLZkaGONMQiByUbA%3D\"",
            "}",
        ).joinToString("") { "<div class=\"FixedLine\">$it</div>" }
        val file = File(context.cacheDir, "code-table.epub")
        ZipOutputStream(file.outputStream()).use { zip ->
            val mime = "application/epub+zip".toByteArray()
            zip.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED; size = mime.size.toLong(); compressedSize = size; crc = CRC32().apply { update(mime) }.value
            })
            zip.write(mime); zip.closeEntry()
            fun text(path: String, content: String) { zip.putNextEntry(ZipEntry(path)); zip.write(content.toByteArray()); zip.closeEntry() }
            text("META-INF/container.xml", """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            text("OEBPS/content.opf", """<?xml version="1.0" encoding="UTF-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="uid">urn:uuid:code-table</dc:identifier><dc:title>Code</dc:title><dc:language>en</dc:language><meta property="dcterms:modified">2026-01-01T00:00:00Z</meta></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/><item id="css" href="s.css" media-type="text/css"/></manifest><spine><itemref idref="c1"/></spine></package>""")
            text("OEBPS/nav.xhtml", """<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Nav</title></head><body><nav epub:type="toc"><ol><li><a href="c1.xhtml">Code</a></li></ol></nav></body></html>""")
            text("OEBPS/s.css", css)
            text("OEBPS/c1.xhtml", """<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>Code</title><link href="s.css" rel="stylesheet" type="text/css"/></head><body><p>A data resource generates the token, as shown next.</p><div class="ProgramCode">$code</div></body></html>""")
        }
        return file
    }
}
