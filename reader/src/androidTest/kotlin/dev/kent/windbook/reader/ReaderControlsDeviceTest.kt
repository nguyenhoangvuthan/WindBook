package dev.kent.windbook.reader

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.lifecycleScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.kent.windbook.core.model.*
import dev.kent.windbook.reader.formats.TxtAdapter
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class ReaderControlsDeviceTest {
    @Test fun textContentsSearchSeekAndTypographyUseTheSameSession() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val file=File(context.cacheDir,"reader-controls.txt").apply {
            writeText((1..1800).joinToString("\n") {"Đoạn $it: Tiếng Việt có dấu. Câu chuyện tổng hợp để kiểm tra điều hướng và cỡ chữ."})
        }
        val services=ReadiumServices(context)
        val session=runBlocking {ReaderEngine(services,adapters=listOf(TxtAdapter(services))).open(file,BookFormat.TXT).getOrThrow()}
        val controller=ReaderNavigationController(session)
        val target=runBlocking {
            assertTrue(controller.reflowable)
            assertTrue(controller.contents().size>1)
            val cursor=controller.search("Tiếng Việt")!!
            try {
                val first=cursor.next()
                assertEquals(50,first.size)
                assertTrue(first.all {it.snippet.contains("Tiếng Việt")})
                assertEquals(50,cursor.next().size)
            } finally {cursor.close()}
            controller.position(.8)!!
        }
        val seen=CountDownLatch(1)
        val location=AtomicReference("")
        val scenario=launchFragmentInContainer<ReaderHostFragment>(fragmentArgs=ReaderHostFragment.arguments(NavigationSurface.PUBLICATION),themeResId=androidx.appcompat.R.style.Theme_AppCompat_DayNight_NoActionBar)
        try {
            scenario.onFragment {fragment -> fragment.bind(session,null,object: ReaderHostFragment.Callbacks {
                override fun onLocatorChanged(locatorJson: String,progression: Double,chapterTitle: String?) {location.set(locatorJson);seen.countDown()}
                override fun onCenterTap()=Unit
            },false)}
            assertTrue(seen.await(20,TimeUnit.SECONDS))
            val moved=CountDownLatch(1);var result=NavigationResult.FAILED
            scenario.onFragment {fragment ->fragment.lifecycleScope.launch {result=fragment.navigate(target);moved.countDown()}}
            assertTrue(moved.await(10,TimeUnit.SECONDS));assertEquals(NavigationResult.COMPLETED,result)
            assertEquals(org.json.JSONObject(target).getString("href"),org.json.JSONObject(location.get()).getString("href"))
            val saved=location.get()
            assertNotNull("Visible text anchors must include a semantic selector",saved.toLocatorOrNull()!!.locations.otherLocations["cssSelector"])
            val before=saved.toLocatorOrNull()!!.href
            val away=runBlocking {controller.position(.1)!!}
            val awayDone=CountDownLatch(1)
            scenario.onFragment {fragment->fragment.lifecycleScope.launch {fragment.navigate(away);awayDone.countDown()}}
            assertTrue(awayDone.await(10,TimeUnit.SECONDS))
            scenario.onFragment {it.applyTypography(ReaderTypography(size=160,font=ReaderFont.SERIF,colors=ReaderColors.DARK),false)}
            Thread.sleep(1200)
            scenario.onFragment {assertEquals(org.readium.r2.navigator.preferences.Theme.DARK,it.epubTheme)}
            val returned=CountDownLatch(1)
            var back=NavigationResult.FAILED
            var paragraphVisible=false
            scenario.onFragment {fragment->fragment.lifecycleScope.launch {
                back=fragment.navigate(saved)
                val navigator=fragment.childFragmentManager.fragments.filterIsInstance<org.readium.r2.navigator.epub.EpubNavigatorFragment>().single()
                paragraphVisible=navigator.evaluateJavascript(navigationVisibilityScript(saved.toLocatorOrNull()!!)!!)=="true"
                returned.countDown()
            }}
            assertTrue(returned.await(10,TimeUnit.SECONDS))
            assertEquals(NavigationResult.COMPLETED,back)
            assertTrue("The same saved paragraph must be visible after re-pagination",paragraphVisible)
            assertEquals(before,location.get().toLocatorOrNull()!!.href)
            scenario.onFragment {it.applyTypography(ReaderTypography(),false)}
            Thread.sleep(800)
            scenario.onFragment {assertEquals(org.readium.r2.navigator.preferences.Theme.SEPIA,it.epubTheme)}
        } finally {scenario.close();session.close()}
    }
}
