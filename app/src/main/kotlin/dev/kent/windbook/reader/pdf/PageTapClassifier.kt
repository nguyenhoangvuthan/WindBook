package dev.kent.windbook.reader.pdf

import kotlin.math.abs

/** What a tap on a paged PDF page means. */
internal enum class TapAction { PREVIOUS_PAGE, NEXT_PAGE, TOGGLE_MENU, ZOOM_TOGGLE }

/**
 * Splits a paged page into tap zones so a page turn can never be read as a double-tap zoom.
 *
 * The outer quarters are plain taps that act immediately (two quick taps turn two pages).
 * Only the middle half can become a double tap: its first tap waits; a second middle tap
 * within [slopPx] while it waits zooms, and when the caller's double-tap timeout elapses it
 * calls [flushPending], which turns the lone tap into a menu toggle. The classifier owns no
 * clock: the caller owns the wait (a coroutine delay), which keeps this pure and testable.
 */
internal class PageTapClassifier(private val slopPx: Float = 48f) {
    private var pendingX = 0f
    private var pendingY = 0f
    private var hasPending = false

    /** True while a middle tap is waiting for a possible second tap. */
    val isWaiting: Boolean get() = hasPending

    /** The action to run now, or null for a middle tap that now waits for a possible second tap. */
    fun onTap(x: Float, y: Float, viewportWidth: Float): TapAction? {
        when {
            x < viewportWidth * EDGE -> { hasPending = false; return TapAction.PREVIOUS_PAGE }
            x > viewportWidth * (1f - EDGE) -> { hasPending = false; return TapAction.NEXT_PAGE }
        }
        if (hasPending && abs(x - pendingX) <= slopPx && abs(y - pendingY) <= slopPx) {
            hasPending = false
            return TapAction.ZOOM_TOGGLE
        }
        // A new first tap; an older waiting tap that did not pair up is superseded.
        hasPending = true
        pendingX = x
        pendingY = y
        return null
    }

    /** The double-tap timeout elapsed: a lone middle tap becomes a menu toggle, once. */
    fun flushPending(): TapAction? {
        if (!hasPending) return null
        hasPending = false
        return TapAction.TOGGLE_MENU
    }

    private companion object {
        const val EDGE = 0.25f
    }
}
