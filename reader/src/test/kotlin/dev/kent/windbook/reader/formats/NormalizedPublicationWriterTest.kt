package dev.kent.windbook.reader.formats

import dev.kent.windbook.reader.source.SessionWorkspace
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.zip.ZipFile
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NormalizedPublicationWriterTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun write(root: File): File = runBlocking {
        val lease = SessionWorkspace(File(root, "out")).create()
        val chapter = lease.stage("""<html xmlns="http://www.w3.org/1999/xhtml"><head/><body><p>One &amp; two</p><p>Next</p><img src="../images/a.png"/></body></html>""".byteInputStream())
        val image = lease.stage(byteArrayOf(1, 2, 3).inputStream())
        NormalizedPublicationWriter().write(NormalizedDocument("stable&identity", "Title & <text>",
            listOf(NormalizedSection("text/0001.xhtml", "One & two", chapter), NormalizedSection("text/0002.xhtml", null, chapter)),
            listOf(NormalizedResource("images/a.png", "image/png", image))), lease)
    }
    @Test fun deterministicArchiveContainsActualSpineIdsAndLocalResources() {
        val first = write(tmp.newFolder("first"))
        val second = write(tmp.newFolder("second"))
        assertArrayEquals(first.readBytes(), second.readBytes())
        ZipFile(first).use { zip ->
            val entries = zip.entries().toList()
            assertEquals("mimetype", entries.first().name)
            assertEquals(0, entries.first().method)
            fun text(name: String) = zip.getInputStream(zip.getEntry(name)).bufferedReader().readText()
            val opf = text("EPUB/package.opf")
            assertTrue(opf.contains("text/0001.xhtml")); assertTrue(opf.contains("text/0002.xhtml"))
            assertTrue(opf.contains("Title &amp; &lt;text&gt;"))
            assertTrue(opf.indexOf("idref=\"s1\"") < opf.indexOf("idref=\"s2\""))
            val chapter = text("EPUB/text/0001.xhtml")
            assertTrue(chapter.contains("id=\"p000001\"")); assertTrue(chapter.contains("id=\"p000002\""))
            assertTrue(chapter.contains("../images/a.png"))
            assertNotNull(zip.getEntry("EPUB/images/a.png"))
        }
    }
    @Test fun rejectsActiveExternalAndEscapingContent() = runBlocking {
        listOf("<script>alert(1)</script>", "<body background=\"https://example.org/x\"/>", "<img src=\"https://example.org/x\"/>", "<img src=\"../../escape.png\"/>", "<img src=\"../images/missing.png\"/>").forEachIndexed { index, body ->
            val root = tmp.newFolder("bad$index")
            val lease = SessionWorkspace(File(root, "out")).create()
            val input = lease.stage("<html xmlns=\"http://www.w3.org/1999/xhtml\"><body>$body</body></html>".byteInputStream())
            assertThrows(Exception::class.java) { runBlocking { NormalizedPublicationWriter().write(NormalizedDocument("id", "title", listOf(NormalizedSection("text/0001.xhtml", null, input)), emptyList()), lease) } }
        }
    }
    @Test fun generatedBytesShareScratchBudgetAndFailureRollsBackOutputOnly() = runBlocking {
        val workspace = SessionWorkspace(tmp.newFolder("bounded"), dev.kent.windbook.reader.source.SourceLimits(scratchBytes = 1024, freeReserveBytes = 0))
        val lease = workspace.create()
        val input = lease.stage("<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><p>Text</p></body></html>".byteInputStream())
        val sourceBytes = workspace.usedBytes
        assertThrows(dev.kent.windbook.reader.source.SourceSpaceException::class.java) {
            runBlocking { NormalizedPublicationWriter().write(NormalizedDocument("id", "title", listOf(NormalizedSection("text/0001.xhtml", null, input)), emptyList()), lease) }
        }
        assertEquals(sourceBytes, workspace.usedBytes)
        assertEquals(listOf(input), lease.directory.listFiles()!!.toList())
        lease.close()
        assertEquals(0L, workspace.usedBytes)
    }
    @Test fun rejectsSectionBeyondTextLimitAndRetainsStagedSource() = runBlocking {
        val lease = SessionWorkspace(tmp.newFolder("large")).create()
        val input = lease.stage(("<html><body>" + "x".repeat(65536) + "</body></html>").byteInputStream())
        assertThrows(IllegalArgumentException::class.java) { runBlocking {
            NormalizedPublicationWriter().write(NormalizedDocument("id", "title", listOf(NormalizedSection("text/0001.xhtml", null, input)), emptyList()), lease)
        } }
        assertEquals(listOf(input), lease.directory.listFiles()!!.toList())
        lease.close()
    }

    @Test fun preservesSemanticAnchorsAndPackagesBoundedPassiveStyles() = runBlocking {
        val lease = SessionWorkspace(tmp.newFolder("anchors")).create()
        try {
            val input = lease.stage("<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><link rel=\"stylesheet\" href=\"../styles/book.css\"/></head><body><p id=\"note-1\">Note</p><p><a href=\"#note-1\">Reference</a></p></body></html>".byteInputStream())
            val style = lease.stage("p { margin: 1em; }".byteInputStream())
            val epub = NormalizedPublicationWriter().write(NormalizedDocument("id", "title", listOf(NormalizedSection("text/0001.xhtml", null, input)), listOf(NormalizedResource("styles/book.css", "text/css", style))), lease)
            ZipFile(epub).use { zip ->
                val chapter = zip.getInputStream(zip.getEntry("EPUB/text/0001.xhtml")).bufferedReader().readText()
                assertTrue(chapter.contains("id=\"note-1\""))
                assertTrue(chapter.contains("href=\"#note-1\""))
                assertEquals("p { margin: 1em; }", zip.getInputStream(zip.getEntry("EPUB/styles/book.css")).bufferedReader().readText())
            }
        } finally { lease.close() }
        Unit
    }

    @Test fun refusesUnaccountedInputsEvenInsideSessionDirectory() = runBlocking {
        val lease = SessionWorkspace(tmp.newFolder("unaccounted")).create()
        try {
            val input = File(lease.directory, "untracked.xhtml").apply { writeText("<html xmlns=\"http://www.w3.org/1999/xhtml\"><body/></html>") }
            assertThrows(IllegalArgumentException::class.java) { runBlocking {
                NormalizedPublicationWriter().write(NormalizedDocument("id", "title", listOf(NormalizedSection("text/0001.xhtml", null, input)), emptyList()), lease)
            } }
        } finally { lease.close() }
        Unit
    }

    @Test fun rejectsProcessingInstructionsAnywhereAndReleasesPartialOutput() = runBlocking {
        val instruction = "<?xml-stylesheet type=\"text/css\" href=\"https://example.org/book.css\"?>"
        for ((index, text) in listOf(
            instruction + "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><p>Text</p></body></html>",
            "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body>$instruction<p>Text</p></body></html>",
            "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><p>Text</p></body></html><?custom local?>",
        ).withIndex()) {
            val workspace = SessionWorkspace(tmp.newFolder("instruction$index"))
            val lease = workspace.create()
            try {
                val input = lease.stage(text.byteInputStream())
                val reserved = workspace.usedBytes
                val error = assertThrows(IllegalArgumentException::class.java) { runBlocking {
                    NormalizedPublicationWriter().write(NormalizedDocument("id", "title", listOf(NormalizedSection("text/0001.xhtml", null, input)), emptyList()), lease)
                } }
                assertTrue(error.message.orEmpty().contains("Processing instructions"))
                assertEquals(reserved, workspace.usedBytes)
                assertEquals(listOf(input), lease.directory.listFiles()!!.toList())
            } finally { lease.close() }
            assertEquals(0L, workspace.usedBytes)
        }
    }

    @Test fun serializedSectionMustFitLimitAfterIdsAndXmlDeclaration() = runBlocking {
        val workspace = SessionWorkspace(tmp.newFolder("serialized-limit"))
        val lease = workspace.create()
        try {
            val prefix = "<html xmlns=\"http://www.w3.org/1999/xhtml\"><body><p>"
            val suffix = "</p></body></html>"
            val text = prefix + "x".repeat(65520 - prefix.length - suffix.length) + suffix
            assertEquals(65520, text.length)
            val input = lease.stage(text.byteInputStream())
            val reserved = workspace.usedBytes
            val error = assertThrows(Exception::class.java) { runBlocking {
                NormalizedPublicationWriter().write(NormalizedDocument("id", "title", listOf(NormalizedSection("text/0001.xhtml", null, input)), emptyList()), lease)
            } }
            assertTrue(generateSequence(error as Throwable?) { it.cause }.any { it.message.orEmpty().contains("Serialized section exceeds") })
            assertEquals(reserved, workspace.usedBytes)
            assertEquals(listOf(input), lease.directory.listFiles()!!.toList())
        } finally { lease.close() }
        assertEquals(0L, workspace.usedBytes)
    }

}
