package dev.kent.windbook.reader

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.testing.TestBooks
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderHostFragmentTest {

    @Test
    fun bindingAnEpubShowsNavigatorAndReportsLocator() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val epub = TestBooks.epub(context.cacheDir, name = "host-test.epub")
        val session = runBlocking {
            ReaderEngine(ReadiumServices(context)).open(epub, BookFormat.EPUB).getOrThrow()
        }
        val locatorSeen = CountDownLatch(1)
        var reported = -1.0
        var reportedJson = ""

        val scenario = launchFragmentInContainer<ReaderHostFragment>(
            fragmentArgs = ReaderHostFragment.arguments(BookFormat.EPUB),
            themeResId = androidx.appcompat.R.style.Theme_AppCompat_DayNight_NoActionBar,
        )
        scenario.onFragment { fragment ->
            fragment.bind(session, initialLocatorJson = null, callbacks = object : ReaderHostFragment.Callbacks {
                override fun onLocatorChanged(locatorJson: String, progression: Double, chapterTitle: String?) {
                    reported = progression
                    reportedJson = locatorJson
                    locatorSeen.countDown()
                }
                override fun onCenterTap() = Unit
            })
        }

        assertTrue("navigator never reported a locator", locatorSeen.await(15, TimeUnit.SECONDS))
        assertTrue(reportedJson.contains("href"))
        assertTrue(reported in 0.0..1.0)
        scenario.close()
        session.close()
    }
}
