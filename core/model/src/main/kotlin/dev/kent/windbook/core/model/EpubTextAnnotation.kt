package dev.kent.windbook.core.model

/** Semantic single-resource anchor. The JSON is decoded and cross-checked at persistence boundaries. */
data class EpubTextAnchor(val version: Int = 1, val href: String, val locatorJson: String,
    val quote: String, val before: String = "", val after: String = "") {
    fun isValid(): Boolean = version == 1 && href.isNotBlank() && href.length <= 4096 &&
        !href.startsWith('/') && !href.contains(':') && !href.contains('\\') &&
        safeResource(href) &&
        locatorJson.isNotBlank() && locatorJson.length <= 32_768 &&
        quote.isNotBlank() && quote.length <= 4096 && before.length <= 512 && after.length <= 512
    private fun safeResource(value:String):Boolean = try {
        val decoded=java.net.URLDecoder.decode(value.replace("+","%2B"),"UTF-8")
        !decoded.startsWith('/') && !decoded.contains(':') && !decoded.contains('\\') &&
            !decoded.contains('%') && decoded.none {it.code<32} && decoded.split('/').none {it==".." || it=="."}
    } catch(_:Exception) {false}
}
