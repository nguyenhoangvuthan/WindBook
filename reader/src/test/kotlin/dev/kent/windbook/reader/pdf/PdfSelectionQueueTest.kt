package dev.kent.windbook.reader.pdf

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class PdfSelectionQueueTest {
    @Test fun runsOneRequestAndOnlyNewestPendingAndDiscardsStaleResult() = runTest {
        val gate=CompletableDeferred<Unit>()
        val calls=mutableListOf<Int>(); val delivered=mutableListOf<Int>()
        val queue=PdfSelectionQueue<Int,Int>(backgroundScope,{ value -> calls.add(value); if(value==1) gate.await(); value },{ delivered.add(it) })
        queue.submit(1); runCurrent()
        queue.submit(2); queue.submit(3); runCurrent()
        assertEquals(listOf(1),calls)
        gate.complete(Unit); runCurrent()
        assertEquals(listOf(1,3),calls); assertEquals(listOf(3),delivered)
        queue.close()
    }
    @Test fun invalidationRejectsOutstandingNativeResult() = runTest {
        val gate=CompletableDeferred<Unit>(); val delivered=mutableListOf<Int>()
        val queue=PdfSelectionQueue<Int,Int>(backgroundScope,{ gate.await(); it },{delivered.add(it)})
        queue.submit(1); runCurrent(); queue.invalidate(); gate.complete(Unit); runCurrent()
        assertTrue(delivered.isEmpty()); queue.close()
    }
}
