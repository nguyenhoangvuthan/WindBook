package dev.kent.windbook.reader

import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.testing.TestBooks
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.readium.r2.navigator.preferences.Theme
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
            fragmentArgs = ReaderHostFragment.arguments(NavigationSurface.PUBLICATION),
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
            }, darkTheme = false)
        }

        assertTrue("navigator never reported a locator", locatorSeen.await(15, TimeUnit.SECONDS))
        assertTrue(reportedJson.contains("href"))
        assertTrue(reported in 0.0..1.0)
        scenario.close()
        session.close()
    }
    @Test
    fun recreatingTheHostDoesNotCrashAndRebindsAtTheGivenLocator() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val epub = TestBooks.epub(context.cacheDir, name = "host-recreate.epub")
        val session = runBlocking {
            ReaderEngine(ReadiumServices(context)).open(epub, BookFormat.EPUB).getOrThrow()
        }
        var firstJson = ""
        val first = CountDownLatch(1)
        val scenario = launchFragmentInContainer<ReaderHostFragment>(
            fragmentArgs = ReaderHostFragment.arguments(NavigationSurface.PUBLICATION),
            themeResId = androidx.appcompat.R.style.Theme_AppCompat_DayNight_NoActionBar,
        )
        scenario.onFragment { fragment ->
            fragment.bind(session, null, object : ReaderHostFragment.Callbacks {
                override fun onLocatorChanged(locatorJson: String, progression: Double, chapterTitle: String?) {
                    firstJson = locatorJson
                    first.countDown()
                }
                override fun onCenterTap() = Unit
            }, false)
        }
        assertTrue(first.await(15, TimeUnit.SECONDS))

        // Rotation / config change: the restored Readium navigator must never reach onResume.
        scenario.recreate()

        val again = CountDownLatch(1)
        scenario.onFragment { fragment ->
            fragment.bind(session, firstJson, object : ReaderHostFragment.Callbacks {
                override fun onLocatorChanged(locatorJson: String, progression: Double, chapterTitle: String?) {
                    again.countDown()
                }
                override fun onCenterTap() = Unit
            }, false)
        }
        assertTrue("re-bound navigator never reported a locator", again.await(15, TimeUnit.SECONDS))
        scenario.close()
        session.close()
    }

    @Test
    fun darkModeBindsEpubWithDarkTheme() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val epub = TestBooks.epub(context.cacheDir, name = "host-theme.epub")
        val session = runBlocking {
            ReaderEngine(ReadiumServices(context)).open(epub, BookFormat.EPUB).getOrThrow()
        }
        val seen = CountDownLatch(1)
        val scenario = launchFragmentInContainer<ReaderHostFragment>(
            fragmentArgs = ReaderHostFragment.arguments(NavigationSurface.PUBLICATION),
            themeResId = androidx.appcompat.R.style.Theme_AppCompat_DayNight_NoActionBar,
        )
        scenario.onFragment { fragment ->
            fragment.bind(session, null, object : ReaderHostFragment.Callbacks {
                override fun onLocatorChanged(locatorJson: String, progression: Double, chapterTitle: String?) = seen.countDown()
                override fun onCenterTap() = Unit
            }, darkTheme = true)
        }
        assertTrue(seen.await(15, TimeUnit.SECONDS))
        scenario.onFragment { assertEquals(Theme.DARK, it.epubTheme) }

        // Light mode after a config change (system dark mode toggled off): the re-bound navigator is SEPIA.
        scenario.recreate()
        val again = CountDownLatch(1)
        scenario.onFragment { fragment ->
            fragment.bind(session, null, object : ReaderHostFragment.Callbacks {
                override fun onLocatorChanged(locatorJson: String, progression: Double, chapterTitle: String?) = again.countDown()
                override fun onCenterTap() = Unit
            }, darkTheme = false)
        }
        assertTrue(again.await(15, TimeUnit.SECONDS))
        scenario.onFragment { assertEquals(Theme.SEPIA, it.epubTheme) }
        scenario.close()
        session.close()
    }
}
