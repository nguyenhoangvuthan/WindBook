package dev.kent.windbook.reader

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.lifecycleScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.reader.formats.TxtAdapter
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ReaderNavigationQuoteDeviceTest {
    @Test fun lateAndRepeatedMatchesInsideOneParagraphNavigateToTheirOwnRanges() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.cacheDir,"long-paragraph.txt").apply {writeText("common ".repeat(2500)+"lategateunique "+"common ".repeat(1500))}
        val services=ReadiumServices(context)
        val session=runBlocking {ReaderEngine(services,adapters=listOf(TxtAdapter(services))).open(file,BookFormat.TXT).getOrThrow()}
        val navigation=ReaderNavigationController(session)
        val late=runBlocking {navigation.search("lategateunique")!!.use {it.next().single()}}
        val repeated=runBlocking {navigation.search("common")!!.use {cursor ->var page=emptyList<ReaderSearchHit>();repeat(4) {page=cursor.next()};page.last()}}
        val seen=CountDownLatch(1)
        var saved=""
        val scenario=launchFragmentInContainer<ReaderHostFragment>(fragmentArgs=ReaderHostFragment.arguments(NavigationSurface.PUBLICATION),themeResId=androidx.appcompat.R.style.Theme_AppCompat_DayNight_NoActionBar)
        try {
            scenario.onFragment {it.bind(session,null,object: ReaderHostFragment.Callbacks {
                override fun onLocatorChanged(locatorJson: String,progression: Double,chapterTitle: String?) {saved=locatorJson;seen.countDown()}
                override fun onCenterTap()=Unit
            },false)}
            assertTrue(seen.await(20,TimeUnit.SECONDS))
            for(hit in listOf(late,repeated)) {
                val done=CountDownLatch(1);var result=NavigationResult.FAILED;var exact=false
                scenario.onFragment {fragment->fragment.lifecycleScope.launch {
                    result=fragment.navigate(hit.locatorJson)
                    val navigator=fragment.childFragmentManager.fragments.filterIsInstance<org.readium.r2.navigator.epub.EpubNavigatorFragment>().single()
                    exact=navigator.evaluateJavascript(navigationVisibilityScript(hit.locatorJson.toLocatorOrNull()!!)!!)=="true"
                    done.countDown()
                }}
                assertTrue(done.await(10,TimeUnit.SECONDS))
                assertEquals("A visible paragraph does not mean its late match is visible",NavigationResult.COMPLETED,result)
                assertTrue(exact)
            }
            val restore=saved
            assertTrue((restore.toLocatorOrNull()!!.locations.otherLocations["windbookTextOffset"] as Number).toInt()>500)
            scenario.recreate()
            val restored=CountDownLatch(1)
            scenario.onFragment {fragment->fragment.bind(session,restore,object: ReaderHostFragment.Callbacks {
                override fun onLocatorChanged(locatorJson: String,progression: Double,chapterTitle: String?) {restored.countDown()}
                override fun onCenterTap()=Unit
            },false)}
            assertTrue(restored.await(20,TimeUnit.SECONDS))
            Thread.sleep(1000)
            val checked=CountDownLatch(1);var ownRange=false
            scenario.onFragment {fragment->fragment.lifecycleScope.launch {
                val navigator=fragment.childFragmentManager.fragments.filterIsInstance<org.readium.r2.navigator.epub.EpubNavigatorFragment>().single()
                ownRange=navigator.evaluateJavascript(navigationVisibilityScript(restore.toLocatorOrNull()!!)!!)=="true"
                checked.countDown()
            }}
            assertTrue(checked.await(10,TimeUnit.SECONDS))
            assertTrue("Recreation must restore the saved occurrence rather than its first identical context",ownRange)
        } finally {scenario.close();session.close()}
    }
}
