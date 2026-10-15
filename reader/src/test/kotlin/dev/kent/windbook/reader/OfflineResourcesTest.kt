package dev.kent.windbook.reader

import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.testing.TestBooks
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Error
import org.robolectric.RobolectricTestRunner

/** Messages of an error and all its causes, outermost first. */
internal fun Error.chain(): String = generateSequence(this) { it.cause }.joinToString(" <- ") { it.message }

@RunWith(RobolectricTestRunner::class)
class OfflineResourcesTest {
    @get:Rule val tmp = TemporaryFolder()

    /** What the EPUB navigator asks for when a chapter links a stylesheet that is not in the book. */
    @Test fun missingResourceFailsInsteadOfCrashing() = runTest {
        val services = ReadiumServices(ApplicationProvider.getApplicationContext(), pdfFactory = null)
        val publication = services.openPublication(TestBooks.epub(tmp.root, missingManifestItem = true)).getOrThrow()

        val resource = publication.get(AbsoluteUrl("https://example.invalid/OEBPS/Styles/missing.css")!!)
        val error = resource?.read()?.failureOrNull()

        // Before the fix Readium really goes to the network (and on a device throws SecurityException).
        assertTrue("expected an offline failure, got: ${error?.chain()}", error?.chain()?.contains("WindBook is offline") == true)
        resource?.close()
        publication.close()
    }
}
