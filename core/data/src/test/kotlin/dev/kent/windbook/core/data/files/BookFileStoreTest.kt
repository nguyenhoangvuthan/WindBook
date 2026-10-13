package dev.kent.windbook.core.data.files

import dev.kent.windbook.core.model.BookFormat
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BookFileStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun sha(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test fun copyToTemp_streamsAndHashes() {
        val store = BookFileStore(tmp.root)
        val bytes = ByteArray(200_000) { (it % 251).toByte() }

        val copied = store.copyToTemp(bytes.inputStream(), maxBytes = 1_000_000)

        assertEquals(bytes.size.toLong(), copied.size)
        assertEquals(sha(bytes), copied.sha256)
        assertArrayEquals(bytes, copied.file.readBytes())
    }

    @Test fun copyToTemp_overLimit_throwsAndLeavesNoTempFile() {
        val store = BookFileStore(tmp.root)
        try {
            store.copyToTemp(ByteArray(2_000).inputStream(), maxBytes = 1_000)
            fail("expected FileTooLargeException")
        } catch (e: FileTooLargeException) {
            assertEquals(1_000L, e.limitBytes)
        }
        assertTrue(store.tempDir.listFiles().orEmpty().isEmpty())
    }

    @Test fun commit_movesIntoBooksDirWithExtension() {
        val store = BookFileStore(tmp.root)
        val copied = store.copyToTemp("%PDF-1.4".toByteArray().inputStream(), 100)

        val final = store.commit(copied.file, "id1", BookFormat.PDF)

        assertEquals("id1.pdf", final.name)
        assertEquals(store.booksDir, final.parentFile)
        assertFalse(copied.file.exists())
    }

    @Test fun readHeader_returnsAtMost1024Bytes() {
        val store = BookFileStore(tmp.root)
        val f = tmp.newFile("big").apply { writeBytes(ByteArray(5_000) { 1 }) }
        assertEquals(1024, store.readHeader(f).size)
        val small = tmp.newFile("small").apply { writeBytes(ByteArray(10)) }
        assertEquals(10, store.readHeader(small).size)
    }

    @Test fun delete_isNullSafe() {
        val store = BookFileStore(tmp.root)
        store.delete(null)
        val cover = store.writeCover("c", byteArrayOf(1, 2, 3))
        store.delete(cover.absolutePath)
        assertFalse(cover.exists())
    }
}
