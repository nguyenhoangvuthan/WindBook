package dev.kent.windbook.core.model

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EpubArchiveGuardTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun zip(vararg entries: Pair<String, ByteArray>): File =
        tmp.newFile().also { file ->
            ZipOutputStream(file.outputStream()).use { output ->
                entries.forEach { (name, bytes) ->
                    output.putNextEntry(ZipEntry(name)); output.write(bytes); output.closeEntry()
                }
            }
        }
    private val container get() = "META-INF/container.xml" to byteArrayOf(1)

    @Test fun validContainerPasses() = assertNull(EpubArchiveGuard().inspect(zip(container)))
    @Test fun zipWithoutContainerIsNotEpub() =
        assertEquals(EpubArchiveError.NOT_AN_EPUB, EpubArchiveGuard().inspect(zip("file" to byteArrayOf(1))))
    @Test fun unsafePathsRemainRejected() {
        for (path in listOf("../file", "a/../../file", "/absolute", "a\\..\\file")) {
            assertEquals(path, EpubArchiveError.UNSAFE_PATHS, EpubArchiveGuard().inspect(zip(container, path to byteArrayOf(1))))
        }
    }
    @Test fun entryLimitRemainsEnforced() =
        assertEquals(EpubArchiveError.TOO_MANY_ENTRIES,
            EpubArchiveGuard(maxEntries = 1).inspect(zip(container, "extra" to byteArrayOf(1))))
    @Test fun expandedSizeLimitRemainsEnforced() =
        assertEquals(EpubArchiveError.TOO_LARGE_UNCOMPRESSED,
            EpubArchiveGuard(maxUncompressedBytes = 20).inspect(zip(container, "large" to ByteArray(30))))
    @Test fun corruptArchiveRemainsRejected() {
        val file = tmp.newFile().apply { writeText("not a zip") }
        assertEquals(EpubArchiveError.CORRUPT, EpubArchiveGuard().inspect(file))
    }
}
