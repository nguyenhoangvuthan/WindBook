package dev.kent.windbook.folders

import android.provider.DocumentsContract
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderLocationTest {
    @Test fun deviceAliasesYieldTheSameExactTreeAndDocumentPickerHint() {
        val a = FolderLocation.resolve("/sdcard/Books/Việt Nam/")!!
        val b = FolderLocation.resolve("/storage/emulated/0/Books/Việt Nam")!!
        assertEquals(a, b)
        assertEquals("primary:Books/Việt Nam", DocumentsContract.getTreeDocumentId(a.treeUri))
        assertEquals("primary:Books/Việt Nam", DocumentsContract.getDocumentId(a.initialUri))
        assertEquals("ABCD-1234:Books", DocumentsContract.getTreeDocumentId(
            FolderLocation.resolve("/storage/ABCD-1234/Books")!!.treeUri))
    }

    @Test fun computerPathsTraversalAndNonTreeUrisNeverBecomeGrants() {
        listOf("/home/kent/Library", "/sdcard/Books/../Secret", "/sdcardish/Books", "/storage/emulated/10/Books",
            "file:///sdcard/Books", "content://folders.test/tree/root/document/child", "content://folders.test/tree/root?x=1")
            .forEach { assertNull(it, FolderLocation.resolve(it)) }
        val tree = FolderLocation.resolve("content://folders.test/tree/root")!!
        assertEquals("root", DocumentsContract.getDocumentId(tree.initialUri))
    }
}
