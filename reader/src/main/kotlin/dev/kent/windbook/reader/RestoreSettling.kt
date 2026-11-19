package dev.kent.windbook.reader

/** The page's viewport and horizontal scroll extent, in CSS pixels. Any change means the text re-flowed. */
internal data class PageLayout(val width: Int, val height: Int, val scrollWidth: Int)

/** Reads the `"width,height,scrollWidth"` string returned by [pageLayoutScript]; null when the page could not measure. */
internal fun parsePageLayout(result: String?): PageLayout? {
    val parts = result?.trim()?.removeSurrounding("\"")?.split(',') ?: return null
    if (parts.size != 3) return null
    val (width, height, scrollWidth) = parts.map { it.trim().toIntOrNull() ?: return null }
    return PageLayout(width, height, scrollWidth)
}

/** How long a restored anchor must stay visible in one layout, how often it is checked, and when restoring gives up. */
internal const val RESTORE_SETTLE_MS = 1000L
internal const val RESTORE_POLL_MS = 150L
internal const val RESTORE_DEADLINE_MS = 8000L

internal const val pageLayoutScript = "innerWidth+','+innerHeight+','+document.scrollingElement.scrollWidth"

/**
 * Decides when a restored reading position is final. A WebView can still re-flow after its first
 * frame (Readium adds its paginated vertical padding once window insets arrive), which moves the
 * anchor to another page. The anchor counts as restored only after it has stayed visible in one
 * unchanged layout for [settleMillis].
 */
internal class RestoreSettling(private val settleMillis: Long) {
    private var layout: PageLayout? = null
    private var since = 0L

    fun observe(now: Long, layout: PageLayout?, anchorVisible: Boolean): Boolean {
        if (layout == null || !anchorVisible || layout != this.layout) {
            this.layout = if (anchorVisible) layout else null
            since = now
            return false
        }
        return now - since >= settleMillis
    }
}
