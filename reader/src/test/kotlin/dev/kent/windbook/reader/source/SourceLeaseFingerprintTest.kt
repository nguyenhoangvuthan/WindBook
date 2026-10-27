package dev.kent.windbook.reader.source

import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SourceLeaseFingerprintTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun stagedFingerprintIsRepeatableAndClosedLeaseRejectsIt() = runBlocking(Dispatchers.IO) {
        val workspace = SessionWorkspace(tmp.newFolder("sessions"))
        val session = workspace.create()
        val staged = session.stageSource(ByteArrayInputStream("abc".toByteArray()))
        val lease = SourceLease(staged.file, session, 4, staged.sha256)
        repeat(2) {
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", lease.fingerprint())
        }
        assertThrows(kotlinx.coroutines.CancellationException::class.java) {
            runBlocking { withContext(Job().apply { cancel() }) { lease.fingerprint() } }
        }
        lease.close()
        assertThrows(IllegalStateException::class.java) { runBlocking { lease.fingerprint() } }
        assertEquals(0, workspace.activeLeaseCount)
    }

    @Test fun ownedFingerprintReadsTheActualBytesOnEachCall() = runBlocking(Dispatchers.IO) {
        val file = tmp.newFile("owned").apply { writeText("abc") }
        SourceLease(file, null, 4).use { lease ->
            assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", lease.fingerprint())
            file.writeBytes(byteArrayOf())
            assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", lease.fingerprint())
        }
    }
}
