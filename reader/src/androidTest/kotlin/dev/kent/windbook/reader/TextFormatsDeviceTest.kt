package dev.kent.windbook.reader

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.reader.formats.TxtAdapter
import dev.kent.windbook.reader.source.SourceAccess
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Run prepare and restore in separate instrumentation processes with a force-stop/cache removal between. */
@RunWith(AndroidJUnit4::class)
@org.junit.FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
class TextFormatsDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val saved get() = File(context.filesDir, "txt-gate.json")
    private val fixture get() = File(context.filesDir, "txt-gate.txt")
    @Test fun prepare() = gate(false)
    @Test fun restoreAfterProcessDeathAndCacheDeletion() = gate(true)
    private fun gate(restore: Boolean) {
        if (!restore) fixture.writeText((1..1600).joinToString("\n") { "Đoạn $it: Tiếng Việt có dấu. Đây là câu chuyện tổng hợp dùng để kiểm tra vị trí đọc sách. <script> & 😀" })
        val previous = if (restore) JSONObject(saved.readText()) else null
        val access = SourceAccess(context)
        val services = ReadiumServices(context, null)
        val engine = ReaderEngine(services, access, listOf(TxtAdapter(services, access.workspace)))
        val session = runBlocking { engine.open(fixture, BookFormat.TXT).getOrThrow() }
        assertEquals(BookFormat.TXT, session.format)
        val locator = AtomicReference("")
        val seen = CountDownLatch(1)
        val scenario = launchFragmentInContainer<ReaderHostFragment>(fragmentArgs = ReaderHostFragment.arguments(NavigationSurface.PUBLICATION), themeResId = androidx.appcompat.R.style.Theme_AppCompat_DayNight_NoActionBar)
        try {
            scenario.onFragment { fragment -> fragment.bind(session, previous?.getString("locator"), object : ReaderHostFragment.Callbacks {
                override fun onLocatorChanged(locatorJson: String, progression: Double, chapterTitle: String?) { locator.set(locatorJson); seen.countDown() }
                override fun onCenterTap() = Unit
            }, false) }
            assertTrue(seen.await(20, TimeUnit.SECONDS))
            Thread.sleep(1500)
            if (!restore) repeat(6) { scenario.onFragment { assertTrue(it.turnPage(true)) }; Thread.sleep(650) }
            Thread.sleep(1000)
            var matchedLocator = ""
            var passage = ""
            repeat(20) {
                if (passage.isEmpty()) {
                    val done = CountDownLatch(1)
                    scenario.onFragment { fragment ->
                        val views = mutableListOf<WebView>()
                        fun visit(view: View) {
                            if (view is WebView && view.isShown && view.alpha > 0f) {
                                val visible = android.graphics.Rect()
                                if (view.getGlobalVisibleRect(visible) && visible.width() > 0 && visible.height() > 0) views.add(view)
                            }
                            if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
                        }
                        visit(fragment.requireView())
                        val snapshot = locator.get()
                        val href = JSONObject(snapshot).getString("href")
                        var pending = views.size
                        if (views.isEmpty()) done.countDown()
                        views.forEach { web ->
                            web.evaluateJavascript("""(function(){return JSON.stringify({url:decodeURIComponent(location.href),text:Array.from(document.querySelectorAll('p')).filter(p=>{let r=p.getBoundingClientRect();return r.right>0&&r.left<innerWidth&&r.bottom>0&&r.top<innerHeight;}).map(p=>p.textContent).join('\\n')});})()""") { json ->
                                if (json != "null") {
                                    val result = JSONObject(org.json.JSONArray("[$json]").getString(0))
                                    if (result.getString("url").contains(href) && snapshot == locator.get() && result.getString("text").isNotEmpty()) {
                                        passage = result.getString("text")
                                        matchedLocator = snapshot
                                    }
                                }
                                if (--pending == 0) done.countDown()
                            }
                        }
                    }
                    assertTrue(done.await(3, TimeUnit.SECONDS))
                    if (passage.isEmpty()) Thread.sleep(300)
                }
            }
            assertTrue("actual visible Vietnamese passage absent", passage.contains("Tiếng Việt"))
            assertTrue(passage.contains("<script>"))
            assertTrue("supplementary codepoint must survive Android serialization", passage.contains("😀"))
            if (restore) assertEquals("exact visible passage after cache rebuild/process death", previous!!.getString("passage"), passage)
            else {
                assertFalse("navigation did not leave opening passage", passage.startsWith("Đoạn 1:"))
                saved.writeText(JSONObject().put("locator", matchedLocator).put("passage", passage).toString())
            }
            android.util.Log.i("WBTxtGate", "restore=$restore passage=${passage.take(100)} locator=${locator.get()}")
        } finally { scenario.close(); session.close() }
    }
}
