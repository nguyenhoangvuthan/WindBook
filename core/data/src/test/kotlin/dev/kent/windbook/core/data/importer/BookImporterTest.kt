package dev.kent.windbook.core.data.importer

import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.core.data.db.WindBookDatabase
import dev.kent.windbook.core.data.files.BookFileStore
import dev.kent.windbook.core.model.ExtractedMetadata
import dev.kent.windbook.core.model.MetadataExtractor
import dev.kent.windbook.testing.TestBooks
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BookImporterTest {
    @get:Rule val tmp = TemporaryFolder()

    private lateinit var db: WindBookDatabase
    private lateinit var store: BookFileStore
    private var ids = 0
    private var metadata = MetadataExtractor { _, _ -> Result.success(ExtractedMetadata("Meta Title", "Meta Author", byteArrayOf(9, 9))) }

    @Before fun setUp() {
        db = WindBookDatabase.inMemory(ApplicationProvider.getApplicationContext(), AndroidSQLiteDriver())
        store = BookFileStore(tmp.newFolder("app"))
    }

    @After fun tearDown() = db.close()

    private fun importer(maxBytes: Long = 500L * 1024 * 1024) = BookImporter(
        dao = db.libraryDao(), files = store, metadata = { f, fmt -> metadata.extract(f, fmt) },
        clock = { 1_000L }, newId = { "id${++ids}" }, maxBytes = maxBytes,
    )

    private fun source(file: File, name: String? = file.name) = object : ImportSource {
        override val displayName = name
        override val sourceUri = "content://test/${file.name}"
        override fun open(): InputStream = file.inputStream()
    }

    @Test fun import_epub_storesBookCopyAndCover() = runTest {
        val result = importer().import(source(TestBooks.epub(tmp.root)))

        assertEquals(ImportResult.Imported("id1"), result)
        val book = db.libraryDao().findById("id1")!!
        assertEquals("Meta Title", book.title)
        assertEquals("Meta Author", book.author)
        assertEquals("EPUB", book.format)
        assertEquals("TO_READ", book.status)
        assertTrue(File(book.filePath).exists())
        assertTrue(File(book.coverPath!!).exists())
        assertTrue(store.tempDir.listFiles().orEmpty().isEmpty())
    }

    @Test fun import_pdf_isDetectedByContentNotName() = runTest {
        val pdf = TestBooks.pdf(tmp.root, "weird-name.bin")
        assertEquals(ImportResult.Imported("id1"), importer().import(source(pdf)))
        assertEquals("PDF", db.libraryDao().findById("id1")!!.format)
    }

    @Test fun import_sameFileTwice_isDuplicate() = runTest {
        val file = TestBooks.epub(tmp.root)
        importer().import(source(file))
        assertEquals(ImportResult.Duplicate("id1"), importer().import(source(file)))
        assertEquals(1, store.booksDir.listFiles()!!.size)
    }

    @Test fun import_afterDelete_revivesTombstonedBook() = runTest {
        val file = TestBooks.epub(tmp.root)
        importer().import(source(file))
        val old = db.libraryDao().findById("id1")!!
        db.libraryDao().softDelete("id1", now = 5)
        File(old.filePath).delete()

        val result = importer().import(source(file))

        assertEquals(ImportResult.Imported("id1"), result)
        val revived = db.libraryDao().findById("id1")!!
        assertNull(revived.deletedAt)
        assertTrue(File(revived.filePath).exists())
    }

    @Test fun import_blankMetadataTitle_fallsBackToFileName() = runTest {
        metadata = MetadataExtractor { _, _ -> Result.success(ExtractedMetadata("  ", null, null)) }
        importer().import(source(TestBooks.epub(tmp.root, name = "My Novel.epub")))
        val book = db.libraryDao().findById("id1")!!
        assertEquals("My Novel", book.title)
        assertNull(book.coverPath)
    }

    @Test fun import_withoutTitleOrDisplayName_usesUntitled() = runTest {
        metadata = MetadataExtractor { _, _ -> Result.success(ExtractedMetadata(null, null, null)) }
        importer().import(source(TestBooks.epub(tmp.root), name = null))
        assertEquals("Untitled", db.libraryDao().findById("id1")!!.title)
    }

    @Test fun import_textFile_isUnsupported() = runTest {
        val txt = tmp.newFile("notes.epub").apply { writeText("just text") }
        assertEquals(ImportResult.Failed(ImportError.UNSUPPORTED_FORMAT), importer().import(source(txt)))
        assertTrue(store.booksDir.listFiles().orEmpty().isEmpty())
    }

    @Test fun import_zipThatIsNotEpub_isRejected() = runTest {
        val zip = TestBooks.zip(tmp.root, "photos.epub", mapOf("a.jpg" to ByteArray(3)))
        assertEquals(ImportResult.Failed(ImportError.NOT_AN_EPUB), importer().import(source(zip)))
    }

    @Test fun import_tooLarge_isRejectedWithoutCopy() = runTest {
        val result = importer(maxBytes = 100).import(source(TestBooks.epub(tmp.root)))
        assertEquals(ImportResult.Failed(ImportError.TOO_LARGE), result)
        assertTrue(store.tempDir.listFiles().orEmpty().isEmpty())
    }

    @Test fun import_metadataFailure_isUnreadableAndCleansUp() = runTest {
        metadata = MetadataExtractor { _, _ -> Result.failure(IllegalStateException("bad")) }
        assertEquals(ImportResult.Failed(ImportError.UNREADABLE), importer().import(source(TestBooks.epub(tmp.root))))
        assertTrue(store.booksDir.listFiles().orEmpty().isEmpty())
        assertNull(db.libraryDao().findBySha("anything"))
    }

    @Test fun import_unreadableSource_isReadFailed() = runTest {
        val broken = object : ImportSource {
            override val displayName = "x.epub"
            override val sourceUri = null
            override fun open(): InputStream = throw java.io.FileNotFoundException("gone")
        }
        assertEquals(ImportResult.Failed(ImportError.READ_FAILED), importer().import(broken))
        assertNotNull(store)
    }
    @Test fun import_coverWriteFailure_failsAndLeavesNoBookFile() = runTest {
        // A plain file where the covers directory should be makes writeCover throw an IOException.
        store.coversDir.parentFile!!.mkdirs()
        store.coversDir.writeText("not a directory")

        val result = importer().import(source(TestBooks.epub(tmp.root)))

        assertTrue(result is ImportResult.Failed)
        assertTrue(store.booksDir.listFiles().orEmpty().isEmpty())
        assertNull(db.libraryDao().findById("id1"))
    }

    @Test fun import_sourceThrowsSecurityException_isReadFailed() = runTest {
        val denied = object : ImportSource {
            override val displayName = "x.epub"
            override val sourceUri = null
            override fun open(): InputStream = throw SecurityException("permission revoked")
        }
        assertEquals(ImportResult.Failed(ImportError.READ_FAILED), importer().import(denied))
    }
}
