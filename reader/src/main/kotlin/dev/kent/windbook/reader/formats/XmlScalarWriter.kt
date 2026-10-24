package dev.kent.windbook.reader.formats

import java.io.Writer

/** Repairs Android Transformer's numeric surrogate pairs with at most 24 pending characters. */
internal class XmlScalarWriter(private val target: Writer, private val limit: Int = 65536) : Writer() {
    private val pending = StringBuilder()
    var units = 0L
        private set
    override fun write(chars: CharArray, offset: Int, length: Int) {
        for (index in offset until offset + length) {
            pending.append(chars[index])
            drain(false)
        }
    }
    private fun emit(value: String, consumed: Int) {
        require(units + value.length <= limit) { "Serialized section exceeds 64 Ki UTF-16 units" }
        units += value.length
        target.write(value)
        pending.delete(0, consumed)
    }
    private fun drain(final: Boolean) {
        while (pending.isNotEmpty()) {
            if (pending[0] != '&') { emit(pending.substring(0, 1), 1); continue }
            if (pending.length < 2 && !final) return
            if (!pending.startsWith("&#")) { emit("&", 1); continue }
            val end = pending.indexOf(";")
            if (end < 0) {
                if (pending.length <= 10 && !final) return
                emit("&", 1); continue
            }
            val first = pending.substring(2, end).toIntOrNull()
            require(first == null || first !in 0xDC00..0xDFFF) { "Unpaired XML surrogate" }
            if (first == null || first !in 0xD800..0xDBFF) { emit(pending.substring(0, end + 1), end + 1); continue }
            val suffix = pending.substring(end + 1)
            if (suffix.length < 2) {
                require(!final && "&#".startsWith(suffix)) { "Unpaired XML surrogate" }
                return
            }
            require(suffix.startsWith("&#")) { "Unpaired XML surrogate" }
            val secondEnd = suffix.indexOf(';')
            if (secondEnd < 0) { require(!final && suffix.length <= 10) { "Unpaired XML surrogate" }; return }
            val low = suffix.substring(2, secondEnd).toIntOrNull()
            require(low != null && low in 0xDC00..0xDFFF) { "Unpaired XML surrogate" }
            emit("&#${Character.toCodePoint(first.toChar(), low.toChar())};", end + 1 + secondEnd + 1)
        }
    }
    fun finish() { drain(true); target.flush() }
    override fun flush() = target.flush()
    override fun close() = finish()
}
