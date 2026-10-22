package dev.kent.windbook.core.data.folders

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderGrantsTest {
    private val resolver = ApplicationProvider.getApplicationContext<Context>().contentResolver
    private val tree = "content://grant.test/tree/root"
    private val read = Intent.FLAG_GRANT_READ_URI_PERMISSION
    private val write = Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    @Test fun persistRead_keepsOnlyReadPermission_andReleaseRemovesIt() {
        val grants = FolderGrants(resolver)
        grants.persistRead(tree, read or write or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        val permission = resolver.persistedUriPermissions.single()
        assertEquals(Uri.parse(tree), permission.uri)
        assertTrue(permission.isReadPermission)
        assertFalse(permission.isWritePermission)
        assertTrue(grants.hasRead(tree))
        grants.releaseRead(tree)
        assertFalse(grants.hasRead(tree))
        assertTrue(resolver.persistedUriPermissions.isEmpty())
    }

    @Test fun missingReadFlag_doesNotPersistAnything() {
        try { FolderGrants(resolver).persistRead(tree, write); fail("Read permission required") }
        catch (_: SecurityException) { }
        assertTrue(resolver.persistedUriPermissions.isEmpty())
    }

    @Test fun writeOnlyGrant_isNotReadable_andOtherTreeDoesNotMatch() {
        resolver.takePersistableUriPermission(Uri.parse(tree), write)
        val grants = FolderGrants(resolver)
        assertFalse(grants.hasRead(tree))
        grants.persistRead("content://grant.test/tree/other", read)
        assertFalse(grants.hasRead(tree))
        assertTrue(grants.hasRead("content://grant.test/tree/other"))
    }

    @Test fun releaseAlreadyRevokedGrant_isIdempotent() {
        val grants = FolderGrants(resolver)
        grants.persistRead(tree, read)
        resolver.releasePersistableUriPermission(Uri.parse(tree), read)
        grants.releaseRead(tree)
        grants.releaseRead(tree)
        assertTrue(resolver.persistedUriPermissions.isEmpty())
    }

    @Test fun rawPath_isRejectedWithoutPersisting() {
        try { FolderGrants(resolver).persistRead("/storage/emulated/0/Books", read); fail("Tree required") }
        catch (_: IllegalArgumentException) { }
        assertTrue(resolver.persistedUriPermissions.isEmpty())
    }
}
