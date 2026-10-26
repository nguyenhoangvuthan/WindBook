package dev.kent.windbook.reader.pdf

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/** Single consumer bounds native work to one running plus the newest pending gesture. Main-thread owned. */
class PdfSelectionQueue<Q,R>(scope: CoroutineScope, work: suspend (Q)->R, deliver: (R)->Unit) : AutoCloseable {
    private var generation=0L
    private val pending=Channel<Pair<Long,Q>>(Channel.CONFLATED)
    private val worker=scope.launch {
        for((id,request) in pending) {
            if(id!=generation) continue
            val result=work(request)
            if(id==generation) deliver(result)
        }
    }
    fun submit(request: Q) { generation++; pending.trySend(generation to request) }
    fun invalidate() { generation++; while(pending.tryReceive().isSuccess) { /* Drop pending work. */ } }
    override fun close() { invalidate(); pending.close(); worker.cancel() }
}
