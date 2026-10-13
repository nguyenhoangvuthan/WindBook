package dev.kent.windbook.core.model

import java.text.Normalizer

/** Lower-cases and strips diacritics so "tieng viet" matches "Tiếng Việt". */
object SearchText {
    private val combiningMarks = "\\p{Mn}+".toRegex()

    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .replace('đ', 'd')
}
