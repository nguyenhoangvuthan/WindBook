package dev.kent.windbook.core.data.importer

import dev.kent.windbook.testing.TestBooks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ZipGuardTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun validEpubPasses() =
        assertNull(ZipGuard().inspectEpub(TestBooks.epub(tmp.root)))

    @Test fun zipWithoutContainerIsNotAnEpub() {
        val zip = TestBooks.zip(tmp.root, "x.zip", mapOf("readme.txt" to "hi".toByteArray()))
        assertEquals(ImportError.NOT_AN_EPUB, ZipGuard().inspectEpub(zip))
    }

    @Test fun parentTraversalIsRejected() {
        val zip = TestBooks.zip(
            tmp.root, "evil.epub",
            mapOf("META-INF/container.xml" to ByteArray(1), "../../evil.so" to ByteArray(1)),
        )
        assertEquals(ImportError.UNSAFE_PATHS, ZipGuard().inspectEpub(zip))
    }

    @Test fun tooManyEntriesIsRejected() {
        val entries = (0 until 6).associate { "f$it" to ByteArray(1) } + ("META-INF/container.xml" to ByteArray(1))
        val zip = TestBooks.zip(tmp.root, "many.epub", entries)
        assertEquals(ImportError.TOO_MANY_ENTRIES, ZipGuard(maxEntries = 5).inspectEpub(zip))
    }

    @Test fun zipBombIsRejected() {
        val zip = TestBooks.zip(
            tmp.root, "bomb.epub",
            mapOf("META-INF/container.xml" to ByteArray(1), "big" to ByteArray(5_000)),
        )
        assertEquals(ImportError.TOO_LARGE_UNCOMPRESSED, ZipGuard(maxUncompressedBytes = 1_000).inspectEpub(zip))
    }

    @Test fun truncatedZipIsCorrupt() {
        val good = TestBooks.epub(tmp.root)
        val broken = tmp.newFile("broken.epub").apply { writeBytes(good.readBytes().copyOf(60)) }
        assertEquals(ImportError.CORRUPT, ZipGuard().inspectEpub(broken))
    }
}
