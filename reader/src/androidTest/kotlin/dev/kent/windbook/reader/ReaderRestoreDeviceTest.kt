package dev.kent.windbook.reader

import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.lifecycleScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.kent.windbook.core.model.BookFormat
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.epub.EpubNavigatorFragment

/** Reopening an EPUB must show the passage the reader left, even when the page re-flows after it opens. */
@RunWith(AndroidJUnit4::class)
class ReaderRestoreDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun restoredPassageStaysVisibleWhenThePageReflowsAfterOpening() = assertRestoredThroughLateReflow("restore-reflow.epub") {}

    /** Touching the page while the book is still opening is not a choice of a new place. */
    @Test fun dragWhileOpeningDoesNotAbandonTheRestore() = assertRestoredThroughLateReflow("restore-drag.epub") { scenario ->
        Thread.sleep(400)
        slowDrag(scenario)
    }

    private fun assertRestoredThroughLateReflow(name: String, whileOpening: (FragmentScenario<ReaderHostFragment>) -> Unit) {
        val session = runBlocking { ReaderEngine(ReadiumServices(context)).open(noIdEpub(name), BookFormat.EPUB).getOrThrow() }
        val scenario = host()
        try {
            val first = CountDownLatch(1)
            val href = AtomicReference<String>()
            scenario.onFragment { it.bind(session, null, callbacks { json -> href.set(json.toLocatorOrNull()!!.href.toString()); first.countDown() }, false) }
            assertTrue(first.await(20, TimeUnit.SECONDS))
            Thread.sleep(1000)
            // A paragraph that starts low on the first page: halving the page height pushes it to the second one.
            val lowParagraph = evaluate(scenario, """(function(){
                const ps=Array.from(document.body.children);let k=-1;
                ps.forEach((p,i)=>{const r=p.getBoundingClientRect();if(r.left>=0&&r.left<innerWidth&&r.top>innerHeight*0.6&&r.top<innerHeight*0.9&&k<0)k=i;});
                return k<0?null:(k+1)+'|'+ps[k].textContent.slice(0,60);})()""").removeSurrounding("\"")
            assertTrue("no paragraph starts low on the first page: $lowParagraph", lowParagraph.contains('|'))
            val (child, quote) = lowParagraph.split('|', limit = 2)
            val anchor = JSONObject().put("href", href.get()).put("type", "application/xhtml+xml")
                .put("locations", JSONObject().put("cssSelector", ":root > :nth-child(2) > :nth-child($child)").put("windbookTextOffset", 0).put("progression", 0.0).put("position", 1))
                .put("text", JSONObject().put("highlight", quote)).toString()

            scenario.recreate()
            val reopened = CountDownLatch(1)
            val opened = System.currentTimeMillis()
            scenario.onFragment { it.bind(session, anchor, callbacks { reopened.countDown() }, false) }
            whileOpening(scenario)
            // Readium applies its paginated padding only after the page has loaded; emulate a large late re-flow.
            Thread.sleep((opened + 900 - System.currentTimeMillis()).coerceAtLeast(0))
            scenario.onFragment { it.requireView().setPadding(0, 0, 0, it.requireView().height / 2) }
            assertTrue("the reader never settled and reported its position", reopened.await(20, TimeUnit.SECONDS))
            Thread.sleep(1000)

            val visible = evaluate(scenario, navigationVisibilityScript(anchor.toLocatorOrNull()!!)!!)
            assertEquals("the restored passage scrolled off screen after the re-flow", "true", visible)
        } finally { scenario.close(); session.close() }
    }

    @Test fun failedRestoreKeepsTheSavedPositionUntilTheReaderTurnsAPage() {
        val session = runBlocking { ReaderEngine(ReadiumServices(context)).open(noIdEpub("restore-missing.epub"), BookFormat.EPUB).getOrThrow() }
        val scenario = host()
        try {
            val href = (session as ReadiumReaderSession).publication.readingOrder.first().url().toString()
            val unreachable = JSONObject().put("href", href).put("type", "application/xhtml+xml")
                .put("locations", JSONObject().put("cssSelector", "#gone").put("windbookTextOffset", 0).put("progression", 0.4).put("position", 3))
                .put("text", JSONObject().put("highlight", "a passage that is no longer in this chapter")).toString()
            val reports = AtomicInteger()
            val moved = CountDownLatch(1)
            scenario.onFragment { it.bind(session, unreachable, callbacks { reports.incrementAndGet(); moved.countDown() }, false) }
            // Longer than the restore deadline: giving up must not overwrite the saved position.
            Thread.sleep(11_000)
            assertEquals("a failed restore reported (and so saved) a position the reader never chose", 0, reports.get())

            scenario.onFragment { assertTrue(it.turnPage(forward = true)) }
            assertTrue("turning a page after a failed restore must save again", moved.await(10, TimeUnit.SECONDS))
        } finally { scenario.close(); session.close() }
    }

    /** A short vertical drag: enough for Readium to report a drag, too slow and short to turn a page. */
    private fun slowDrag(scenario: FragmentScenario<ReaderHostFragment>) {
        val at = IntArray(2)
        var width = 0; var height = 0
        scenario.onFragment { val view = it.requireView(); view.getLocationOnScreen(at); width = view.width; height = view.height }
        val x = at[0] + width / 2f
        val y = at[1] + height / 2f
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val down = android.os.SystemClock.uptimeMillis()
        fun send(action: Int, dy: Float, time: Long) = instrumentation.sendPointerSync(android.view.MotionEvent.obtain(down, time, action, x, y + dy, 0))
        send(android.view.MotionEvent.ACTION_DOWN, 0f, down)
        for (step in 1..8) send(android.view.MotionEvent.ACTION_MOVE, step * 10f, down + step * 40L)
        send(android.view.MotionEvent.ACTION_UP, 80f, down + 400L)
    }

    private fun host(): FragmentScenario<ReaderHostFragment> = launchFragmentInContainer(
        fragmentArgs = ReaderHostFragment.arguments(NavigationSurface.PUBLICATION),
        themeResId = androidx.appcompat.R.style.Theme_AppCompat_DayNight_NoActionBar,
    )

    private fun callbacks(onLocator: (String) -> Unit) = object : ReaderHostFragment.Callbacks {
        override fun onLocatorChanged(locatorJson: String, progression: Double, chapterTitle: String?) = onLocator(locatorJson)
        override fun onCenterTap() = Unit
    }

    private fun evaluate(scenario: FragmentScenario<ReaderHostFragment>, script: String): String {
        val result = AtomicReference<String?>()
        val done = CountDownLatch(1)
        scenario.onFragment { fragment ->
            fragment.lifecycleScope.launch {
                val navigator = fragment.childFragmentManager.fragments.filterIsInstance<EpubNavigatorFragment>().single()
                result.set(navigator.evaluateJavascript(script))
                done.countDown()
            }
        }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        return result.get() ?: error("the page returned no result")
    }

    /** One chapter of distinct paragraphs without ids, like the book that lost its place. */
    private fun noIdEpub(name: String): File {
        val paragraphs = (1..60).joinToString("") { n ->
            "<p>Paragraph $n opens here. " + "The garden was still wet from the night rain and the water ran over each stone step. ".repeat(3) + "</p>"
        }
        val file = File(context.cacheDir, name)
        ZipOutputStream(file.outputStream()).use { zip ->
            val mime = "application/epub+zip".toByteArray()
            zip.putNextEntry(ZipEntry("mimetype").apply {
                method = ZipEntry.STORED; size = mime.size.toLong(); compressedSize = size; crc = CRC32().apply { update(mime) }.value
            })
            zip.write(mime); zip.closeEntry()
            fun text(path: String, content: String) { zip.putNextEntry(ZipEntry(path)); zip.write(content.toByteArray()); zip.closeEntry() }
            text("META-INF/container.xml", """<?xml version="1.0"?><container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles><rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            text("OEBPS/content.opf", """<?xml version="1.0" encoding="UTF-8"?><package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="uid">urn:uuid:$name</dc:identifier><dc:title>Restore</dc:title><dc:language>en</dc:language><meta property="dcterms:modified">2026-01-01T00:00:00Z</meta></metadata><manifest><item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/></manifest><spine><itemref idref="c1"/></spine></package>""")
            text("OEBPS/nav.xhtml", """<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>Nav</title></head><body><nav epub:type="toc"><ol><li><a href="c1.xhtml">Morning</a></li></ol></nav></body></html>""")
            text("OEBPS/c1.xhtml", """<?xml version="1.0" encoding="UTF-8"?><html xmlns="http://www.w3.org/1999/xhtml"><head><title>Morning</title></head><body><h1>Morning</h1>$paragraphs</body></html>""")
        }
        return file
    }
}
