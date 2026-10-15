package dev.kent.windbook.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.kent.windbook.testing.TestBooks
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.util.AbsoluteUrl

@RunWith(AndroidJUnit4::class)
class OfflineResourcesDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun missingResourceFailsInsteadOfThrowingSecurityException() = runBlocking {
        val publication = ReadiumServices(context).openPublication(TestBooks.epub(context.cacheDir, name = "offline.epub", missingManifestItem = true)).getOrThrow()
        // The navigator's own base URL, exactly as in the crash on the emulator.
        val resource = publication.get(AbsoluteUrl("https://readium/publication/OEBPS/Styles/missing.css")!!)
        val read = resource?.read()
        assertTrue("expected a failure, got $read", read == null || read.isFailure)
        resource?.close()
        publication.close()
    }
}
