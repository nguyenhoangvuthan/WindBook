package dev.kent.windbook.reader.formats

import dev.kent.windbook.core.model.BookFormat
import dev.kent.windbook.reader.ReaderSession
import dev.kent.windbook.reader.source.SourceLease

/** Uses the verified snapshot only. Successful sessions own source cleanup; the engine closes failures. */
interface FormatAdapter {
    val format: BookFormat
    suspend fun open(source: SourceLease): Result<ReaderSession>
}
