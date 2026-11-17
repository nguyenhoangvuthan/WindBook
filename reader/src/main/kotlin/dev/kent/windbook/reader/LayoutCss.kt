package dev.kent.windbook.reader

import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.data.ReadError
import org.readium.r2.shared.util.resource.Resource
import org.readium.r2.shared.util.resource.TransformingResource

private const val STYLE_ID = "wb-layout"

/**
 * Style added to every EPUB chapter. The wrapping rule applies in both layouts so an unbreakable
 * run (the U+FFFD dot leaders some PDF conversions leave in a table of contents, a long URL) can
 * never overflow the column. The other rules sit behind Readium's own selector, which is only
 * present when publisher styles are off, so "keep original formatting" turns them off with no reload.
 */
internal fun layoutCss(): String {
    val clean = ":root[style*=readium-advanced-on]"
    return "html,body,p,li,td,th,a,span,div,h1,h2,h3,h4,h5,h6{overflow-wrap:anywhere}" +
        "$clean a,$clean a:link,$clean a:visited{color:inherit!important;text-decoration:none!important}" +
        // Converted PDFs often split a picture into many image-only paragraphs; remove the gaps between them.
        "$clean p:has(>img:only-child){margin-block:0!important}"
}

/**
 * Adds the layout style once, just before `</head>` (or after the opening `<body>` when there is no head).
 * Bytes outside the insertion are copied unchanged, so text offsets inside the body do not move.
 */
internal fun injectLayoutCss(html: ByteArray): ByteArray {
    val text = String(html, Charsets.UTF_8)
    if (text.contains("id=\"$STYLE_ID\"")) return html
    val style = "<style id=\"$STYLE_ID\">${layoutCss()}</style>"
    val head = Regex("</head\\s*>", RegexOption.IGNORE_CASE).find(text)
    val at = head?.range?.first
        ?: Regex("<body\\b[^>]*>", RegexOption.IGNORE_CASE).find(text)?.range?.last?.plus(1)
        ?: return html
    return (text.substring(0, at) + style + text.substring(at)).toByteArray(Charsets.UTF_8)
}

private fun Url.isHtmlPath(): Boolean {
    val path = path?.lowercase() ?: return false
    return path.endsWith(".xhtml") || path.endsWith(".html") || path.endsWith(".htm")
}

/** Wraps HTML resources so [injectLayoutCss] runs on their bytes; read errors and other resources pass through. */
internal fun wrapHtmlWithLayoutCss(url: Url, resource: Resource): Resource =
    if (!url.isHtmlPath()) resource else object : TransformingResource(resource, cacheBytes = false) {
        override suspend fun transform(data: Try<ByteArray, ReadError>): Try<ByteArray, ReadError> =
            data.map(::injectLayoutCss)
    }
