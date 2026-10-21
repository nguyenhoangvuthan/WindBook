package dev.kent.windbook.core.model

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderScanTest {
    @Test fun defaultsAcceptOnlyEpubAndPdfAndRecurse() {
        val options = ScanOptions()

        assertTrue(options.recursive)
        assertTrue(options.accepts("sample.EpUb"))
        assertTrue(options.accepts("sample.PDF"))
        assertFalse(options.accepts("sample.mobi"))
        assertFalse(options.accepts("sample.azw3"))
        assertFalse(options.accepts("sample.fb2"))
        assertFalse(options.accepts("sample.cbz"))
        assertFalse(options.accepts("sample.cbr"))
        assertFalse(options.accepts("sample.txt"))
    }

    @Test fun candidatesRecognizeAllEightFinalSuffixes() {
        val cases = mapOf(
            "sample.EpUb" to BookFormat.EPUB,
            "sample.PDF" to BookFormat.PDF,
            "sample.MOBI" to BookFormat.MOBI,
            "sample.AZW3" to BookFormat.AZW3,
            "sample.FB2" to BookFormat.FB2,
            "sample.CBZ" to BookFormat.CBZ,
            "sample.CBR" to BookFormat.CBR,
            "sample.TXT" to BookFormat.TXT,
        )

        cases.forEach { (name, expected) -> assertEquals(name, expected, candidateFormat(name)) }
    }

    @Test fun finalSuffixControlsDiscovery() {
        assertFalse(ScanOptions().accepts("sample.pdf.bak"))
        assertNull(candidateFormat("sample.epub.zip"))
        assertEquals(BookFormat.PDF, candidateFormat("sample.backup.pdf"))
        assertEquals(BookFormat.EPUB, candidateFormat("sample.pdf.epub"))
    }

    @Test fun namesWithoutRecognizedSuffixAreNotCandidates() {
        listOf("epub", "PDF", "sample", "sample.", "", "sample.zip").forEach { name ->
            assertNull(name, candidateFormat(name))
        }
    }

    @Test fun customFiltersAcceptOnlySelectedFormats() {
        val options = ScanOptions(extensions = setOf(BookFormat.CBR, BookFormat.TXT), recursive = false)

        assertTrue(options.accepts("sample.CBR"))
        assertTrue(options.accepts("sample.txt"))
        assertFalse(options.accepts("sample.epub"))
        assertFalse(options.accepts("sample.pdf"))
        assertFalse(options.recursive)
    }

    @Test fun emptyFilterAcceptsNothing() {
        val options = ScanOptions(extensions = emptySet())

        listOf("sample.epub", "sample.pdf", "sample.mobi", "sample.azw3", "sample.fb2", "sample.cbz", "sample.cbr", "sample.txt")
            .forEach { name -> assertFalse(name, options.accepts(name)) }
    }

    @Test fun discoveryDoesNotDependOnTurkishDefaultLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))

            assertEquals(BookFormat.MOBI, candidateFormat("sample.MOBI"))
            assertTrue(ScanOptions(extensions = setOf(BookFormat.MOBI)).accepts("sample.MOBI"))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test fun documentIdentityUsesProviderAndDocumentIdAcrossTreeUris() {
        val key = DocumentKey("provider", "42")
        val first = BookSource.External("content://provider/tree/one/document/42", key)
        val overlapping = BookSource.External("content://provider/tree/two/document/42", DocumentKey("provider", "42"))

        assertEquals(DocumentKey("provider", "42"), DocumentKey("provider", "42"))
        assertNotEquals(DocumentKey("provider", "42"), DocumentKey("other", "42"))
        assertNotEquals(DocumentKey("provider", "42"), DocumentKey("provider", "43"))
        assertEquals(1, setOf(first.key, overlapping.key).size)
    }

    @Test fun externalCandidateCanExistWithoutOwnedPathOrHash() {
        val book = Book("external", "Candidate", null, BookFormat.EPUB, null, 1, null, null, ReadingStatus.TO_READ, 1, null)

        assertNull(book.filePath)
        assertNull(book.sha256)
    }
}
