package dev.kent.windbook.core.data.folders

import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsProvider
import androidx.test.core.app.ApplicationProvider
import dev.kent.windbook.core.model.DocumentKey
import dev.kent.windbook.core.model.ScanOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class FolderScannerTest {
    private lateinit var provider: TreeProvider
    private lateinit var access: TreeAccess
    private val tree = "content://scan.test/tree/root"
    private val root = DocumentKey("scan.test", "root")

    @Before fun setup() {
        val info = ProviderInfo().apply {
            authority = "scan.test"
            exported = true
            grantUriPermissions = true
            readPermission = "android.permission.MANAGE_DOCUMENTS"
            writePermission = "android.permission.MANAGE_DOCUMENTS"
        }
        provider = Robolectric.buildContentProvider(TreeProvider::class.java).create(info).get()
        access = DocumentsTreeAccess(ApplicationProvider.getApplicationContext<Context>().contentResolver,
            UnconfinedTestDispatcher())
    }

    @Test fun recursiveScan_filtersAndVisitsRepeatedDirectoriesOnlyOnce_withoutContentAccess() = runTest {
        provider.rows["root"] = listOf(row("p", "root.pdf"), row("sub", "sub", true),
            row("sub", "alias", true), row("denied", "locked", true), row("txt", "skip.txt"))
        provider.rows["sub"] = listOf(row("e", "nested.EPUB"), row("root", "cycle", true))
        provider.denied.add("denied")
        val events = FolderScanner(access).scan(tree, root, ScanOptions()).toList()
        assertEquals(setOf("root.pdf", "sub/nested.EPUB"), events.filterIsInstance<ScanEvent.Found>().map { it.relativePath }.toSet())
        assertEquals(root, events.filterIsInstance<ScanEvent.Found>().first().parentKey)
        assertEquals(1, events.filterIsInstance<ScanEvent.DirectoryFailed>().size)
        assertFalse(events.filterIsInstance<ScanEvent.Finished>().single().complete)
        assertEquals(1, provider.queries.count { it == "sub" })
        assertEquals(1, provider.queries.count { it == "root" })
        assertEquals(0, provider.contentOpens)
        assertEquals(0, provider.writes)
    }

    @Test fun recursionOff_queriesOnlyRoot() = runTest {
        provider.rows["root"] = listOf(row("sub", "sub", true), row("p", "root.pdf"))
        provider.rows["sub"] = listOf(row("e", "nested.epub"))
        val events = FolderScanner(access).scan(tree, root, ScanOptions(recursive = false)).toList()
        assertEquals(listOf("root.pdf"), events.filterIsInstance<ScanEvent.Found>().map { it.relativePath })
        assertEquals(listOf("root"), provider.queries)
        assertTrue(events.filterIsInstance<ScanEvent.Finished>().single().complete)
    }

    @Test fun cursorFailure_preservesEarlierResultsAndReportsIncomplete() = runTest {
        provider.rows["root"] = listOf(row("p", "first.pdf"), row("e", "second.epub"))
        provider.failAfterFirst = true
        val events = FolderScanner(access).scan(tree, root, ScanOptions()).toList()
        assertEquals(listOf("first.pdf"), events.filterIsInstance<ScanEvent.Found>().map { it.relativePath })
        assertEquals(1, events.filterIsInstance<ScanEvent.DirectoryFailed>().size)
        assertFalse(events.filterIsInstance<ScanEvent.Finished>().single().complete)
        assertTrue(provider.cursors.single().isClosed)
    }

    @Test fun cancellationFromCollector_closesActualCursorAndPropagates() = runTest {
        provider.rows["root"] = listOf(row("p", "first.pdf"), row("e", "second.epub"))
        var found = 0
        try {
            FolderScanner(access).scan(tree, root, ScanOptions()).collect {
                if (it is ScanEvent.Found) { found++; throw CancellationException("stop") }
            }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals(1, found)
        assertTrue(provider.cursors.single().isClosed)
    }

    @Test fun cancellingScanJob_closesCursorWhileCollectorIsSuspended() = runTest {
        provider.rows["root"] = listOf(row("p", "first.pdf"), row("e", "second.epub"))
        val found = CompletableDeferred<Unit>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            FolderScanner(access).scan(tree, root, ScanOptions()).collect {
                if (it is ScanEvent.Found) { found.complete(Unit); awaitCancellation() }
            }
        }
        found.await()
        job.cancelAndJoin()
        assertTrue(provider.cursors.single().isClosed)
        assertEquals(0, provider.contentOpens)
    }

    @Test fun nullNames_areExaminedAndSkipped_unknownMetadataRemainsNull() = runTest {
        provider.rows["root"] = listOf(row("n", null), row("p", "book.pdf"))
        val events = FolderScanner(access).scan(tree, root, ScanOptions()).toList()
        assertEquals(2L, events.filterIsInstance<ScanEvent.Examined>().last().count)
        val found = events.filterIsInstance<ScanEvent.Found>().single()
        assertEquals("book.pdf", found.relativePath)
        assertEquals("content://scan.test/tree/root/document/p", found.document.uri)
        assertNull(found.document.size)
        assertNull(found.document.modifiedAt)
        assertFalse(events.filterIsInstance<ScanEvent.Finished>().single().complete)
        assertEquals(DocumentKey("scan.test", "n"), events.filterIsInstance<ScanEvent.Unreadable>().single().key)
    }

    @Test fun crossAuthorityRoot_isRejectedBeforeQuery() = runTest {
        val events = FolderScanner(access).scan(tree, DocumentKey("other", "root"), ScanOptions()).toList()
        assertTrue(provider.queries.isEmpty())
        assertFalse(events.filterIsInstance<ScanEvent.Finished>().single().complete)
    }

    @Test fun directoryOutsideTree_isRejectedBeforeChildQuery() = runTest {
        try {
            access.children(tree, DocumentKey("scan.test", "outside")).toList()
            fail("Outside directory must be rejected")
        } catch (_: SecurityException) { }
        assertTrue(provider.queries.isEmpty())
    }

    @Test fun returnedDocumentOutsideTree_isRejected() = runTest {
        provider.rows["root"] = listOf(row("outside", "escape.pdf"))
        val events = FolderScanner(access).scan(tree, root, ScanOptions()).toList()
        assertTrue(events.filterIsInstance<ScanEvent.Found>().isEmpty())
        assertFalse(events.filterIsInstance<ScanEvent.Finished>().single().complete)
        assertTrue(provider.cursors.single().isClosed)
    }

    @Test fun rawPath_isNeverConvertedIntoUri() = runTest {
        val events = FolderScanner(access).scan("/storage/emulated/0/Books", root, ScanOptions()).toList()
        assertTrue(provider.queries.isEmpty())
        assertFalse(events.filterIsInstance<ScanEvent.Finished>().single().complete)
    }

    @Test fun nullCursor_isIncomplete() = runTest {
        provider.nullCursor = true
        val events = FolderScanner(access).scan(tree, root, ScanOptions()).toList()
        assertEquals(1, events.filterIsInstance<ScanEvent.DirectoryFailed>().size)
        assertFalse(events.filterIsInstance<ScanEvent.Finished>().single().complete)
    }

    @Test fun providerLoadingOrErrorExtras_areNotCompleteScans() = runTest {
        for (extras in listOf(Bundle().apply { putBoolean(DocumentsContract.EXTRA_LOADING, true) },
            Bundle().apply { putString(DocumentsContract.EXTRA_ERROR, "Unavailable") })) {
            provider.cursorExtras = extras
            val events = FolderScanner(access).scan(tree, root, ScanOptions()).toList()
            assertFalse(events.filterIsInstance<ScanEvent.Finished>().single().complete)
            assertEquals(1, events.filterIsInstance<ScanEvent.DirectoryFailed>().size)
            assertTrue(provider.cursors.last().isClosed)
        }
    }

    private fun row(id: String, name: String?, dir: Boolean = false) = arrayOf<Any?>(id, name,
        if (dir) Document.MIME_TYPE_DIR else "application/octet-stream", null, null)
}

/** A real resolver boundary; any content open or source mutation is observable here. */
class TreeProvider : DocumentsProvider() {
    val rows = mutableMapOf<String, List<Array<Any?>>>()
    val denied = mutableSetOf<String>()
    val queries = mutableListOf<String>()
    val cursors = mutableListOf<Cursor>()
    var cursorExtras = Bundle.EMPTY
    var failAfterFirst = false
    var nullCursor = false
    var contentOpens = 0
    var writes = 0
    override fun onCreate() = true
    override fun queryRoots(projection: Array<out String>?) = MatrixCursor(arrayOf("root_id"))
    override fun queryDocument(documentId: String, projection: Array<out String>?) = error("Unexpected document query")
    override fun isChildDocument(parentDocumentId: String, documentId: String) = documentId != "outside"
    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor? {
        queries.add(parentDocumentId)
        if (parentDocumentId in denied) throw SecurityException("Revoked")
        if (nullCursor) return null
        val cursor = object : MatrixCursor(arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED)) {
            override fun getExtras(): Bundle = cursorExtras
            override fun onMove(oldPosition: Int, newPosition: Int): Boolean {
                if (failAfterFirst && newPosition == 1) throw IllegalStateException("Cursor failed")
                return super.onMove(oldPosition, newPosition)
            }
        }
        rows[parentDocumentId].orEmpty().forEach { cursor.addRow(it) }
        cursors.add(cursor)
        return cursor
    }
    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        contentOpens++
        error("Enumeration must not open content")
    }
    override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String { writes++; error("Read only") }
    override fun deleteDocument(documentId: String) { writes++; error("Read only") }
    override fun renameDocument(documentId: String, displayName: String): String { writes++; error("Read only") }
}
