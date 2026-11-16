package dev.kent.windbook.reader

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.lifecycleScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.kent.windbook.core.model.*
import dev.kent.windbook.testing.TestBooks
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class EpubSelectionDeviceTest {
    @Test fun nativeSelectionCapturesSemanticAnchorAndRevisitsAfterTypographyChange() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val session=runBlocking {ReaderEngine(ReadiumServices(context)).open(TestBooks.epub(context.cacheDir,name="selection.epub"),BookFormat.EPUB).getOrThrow()}
        val ready=CountDownLatch(1)
        val activated=CountDownLatch(1)
        val scenario=launchFragmentInContainer<ReaderHostFragment>(fragmentArgs=ReaderHostFragment.arguments(NavigationSurface.PUBLICATION),themeResId=androidx.appcompat.R.style.Theme_AppCompat_DayNight_NoActionBar)
        try {
            scenario.onFragment {it.bind(session,null,object: ReaderHostFragment.Callbacks {
                override fun onLocatorChanged(locatorJson: String,progression: Double,chapterTitle: String?) {ready.countDown()}
                override fun onCenterTap() {}
                override fun onDecorationActivated(id:String) {if(id=="repeat") activated.countDown()}
            },false)}
            assertTrue(ready.await(20,TimeUnit.SECONDS))
            val done=CountDownLatch(1);var anchor:EpubTextAnchor?=null;var failure:Throwable?=null
            scenario.onFragment {host -> host.lifecycleScope.launch {
                try {
                    val nav=host.childFragmentManager.fragments.filterIsInstance<org.readium.r2.navigator.epub.EpubNavigatorFragment>().single()
                    nav.evaluateJavascript("""(function(){const p=document.querySelector('p').firstChild;const r=document.createRange();r.setStart(p,0);r.setEnd(p,5);const s=window.getSelection();s.removeAllRanges();s.addRange(r);return true;})()""")
                    anchor=host.captureSelection()
                    assertEquals("Hello",anchor?.quote)
                    assertTrue(anchor!!.isValid())
                    host.clearSelection()
                    assertNull(host.captureSelection())
                    host.applyAnnotations(listOf(PdfTextAnnotation("mark","b","sha",null,"Hello",AnnotationColor.GOLD,null,1,1,kind="EPUB_TEXT",epubAnchor=anchor)))
                    assertEquals("Hello",nav.evaluateJavascript("window.wbNoteRanges.wbmark.toString()")?.trim('"'))
                    nav.evaluateJavascript("document.querySelector('p').textContent='repeat '.repeat(200)")
                    nav.evaluateJavascript("""(function(){const p=document.querySelector('p').firstChild;const r=document.createRange();r.setStart(p,700);r.setEnd(p,706);const s=window.getSelection();s.removeAllRanges();s.addRange(r);return true;})()""")
                    val repeated=host.captureSelection()!!
                    host.applyAnnotations(listOf(PdfTextAnnotation("repeat","b","sha",null,"repeat",AnnotationColor.GOLD,null,1,1,kind="EPUB_TEXT",epubAnchor=repeated)))
                    val position=nav.evaluateJavascript("window.wbNoteRanges.wbrepeat.startOffset")
                    assertEquals("700",position)
                    nav.evaluateJavascript("""(function(){const r=window.wbNoteRanges.wbrepeat.getClientRects()[0];document.dispatchEvent(new MouseEvent('click',{clientX:r.left+1,clientY:r.top+1,bubbles:true}));return true;})()""")
                    nav.evaluateJavascript("window.wbDrawNotes();true")
                    assertEquals("true",nav.evaluateJavascript("document.querySelectorAll('[data-wb-note-overlay]').length>0"))
                    nav.evaluateJavascript("document.querySelector('p').textContent='Hello WindBook.'")
                    host.applyTypography(ReaderTypography(),true)
                    delay(500)
                    assertNotEquals(NavigationResult.FAILED,host.navigate(anchor!!.locatorJson))
                } catch(e:Throwable){failure=e} finally {done.countDown()}
            }}
            assertTrue(done.await(20,TimeUnit.SECONDS));failure?.let {throw it};assertTrue(activated.await(5,TimeUnit.SECONDS))
        } finally {scenario.close();session.close()}
    }
}
