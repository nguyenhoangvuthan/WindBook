package dev.kent.windbook.reader.source

import java.io.ByteArrayInputStream
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionWorkspaceTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun workspace(source: Long = 20, scratch: Long = 30, space: Long = 100) =
        SessionWorkspace(tmp.root, SourceLimits(source, scratch, 10, 4), usableSpace = { space })

    @Test fun stagedBytesLiveUntilIdempotentRelease() = runBlocking<Unit> {
        val workspace = workspace()
        val lease = workspace.create()
        val file = lease.stage(ByteArrayInputStream("hello".toByteArray()), null)
        assertEquals("hello", file.readText())
        assertEquals(1, workspace.activeLeaseCount)
        assertEquals(5L, workspace.usedBytes)
        lease.close(); lease.close()
        assertFalse(file.exists())
        assertEquals(0, workspace.activeLeaseCount)
        assertEquals(0L, workspace.usedBytes)
    }

    @Test fun knownAndUnknownOversizeLeaveNoPartialFile() = runBlocking<Unit> {
        val workspace = workspace()
        workspace.create().use { lease ->
            assertThrows(SourceTooLargeException::class.java) { runBlocking { lease.stage(ByteArrayInputStream(ByteArray(21)), 21) } }
            assertThrows(SourceTooLargeException::class.java) { runBlocking { lease.stage(ByteArrayInputStream(ByteArray(21)), null) } }
            assertEquals(0, lease.directory.listFiles()!!.size)
            assertEquals(0L, workspace.usedBytes)
        }
    }

    @Test fun sharedScratchAndReserveAreEnforced() = runBlocking<Unit> {
        val workspace = workspace()
        workspace.create().use { first ->
            first.stage(ByteArrayInputStream(ByteArray(20)), 20)
            workspace.create().use { second ->
                assertThrows(SourceSpaceException::class.java) { runBlocking { second.stage(ByteArrayInputStream(ByteArray(11)), null) } }
                assertEquals(20L, workspace.usedBytes)
            }
        }
        workspace(space = 12).create().use { lease ->
            assertThrows(SourceSpaceException::class.java) { runBlocking { lease.stage(ByteArrayInputStream(ByteArray(4)), null) } }
        }
    }

    @Test fun independentManagersPreserveBothActiveLeasesAndRemoveOrphans() = runBlocking<Unit> {
        val first = workspace()
        val a = first.create()
        val aFile = a.stage(ByteArrayInputStream(byteArrayOf(1)), null)
        val second = SessionWorkspace(File(tmp.root, "."), SourceLimits(20, 30, 10, 4)) { 100 }
        assertArrayEquals(byteArrayOf(1), aFile.readBytes())
        val b = second.create()
        b.stage(ByteArrayInputStream(byteArrayOf(2)), null)
        val orphan = File(tmp.root, "orphan").apply { mkdir(); resolve("partial").writeText("old") }
        second.cleanupOrphans()
        assertFalse(orphan.exists())
        assertTrue(a.directory.exists()); assertTrue(b.directory.exists())
        assertEquals(2, first.activeLeaseCount)
        assertEquals(2L, second.usedBytes)
        assertArrayEquals(byteArrayOf(1), aFile.readBytes())
        a.close(); b.close()
    }

    @Test fun cancellationDuringCopyClosesInputAndRemovesPartialBytes() {
        val workspace = workspace()
        var closed = false
        val stream = object : ByteArrayInputStream(ByteArray(12)) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                throw CancellationException("cancel during read")
            }
            override fun close() { closed = true; super.close() }
        }
        workspace.create().use { lease ->
            assertThrows(CancellationException::class.java) { runBlocking { lease.stage(stream, null) } }
            assertTrue(closed)
            assertEquals(0, lease.directory.listFiles()!!.size)
            assertEquals(0L, workspace.usedBytes)
        }
    }
}
