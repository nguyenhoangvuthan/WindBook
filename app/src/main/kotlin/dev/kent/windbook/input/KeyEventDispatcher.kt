package dev.kent.windbook.input

import android.view.KeyEvent
import androidx.compose.runtime.staticCompositionLocalOf

/** Routes hardware page-turn keys (volume, page up/down) to the visible reader. */
class KeyEventDispatcher {

    fun interface PageTurner {
        fun turn(forward: Boolean): Boolean
    }

    @Volatile private var turner: PageTurner? = null

    fun register(turner: PageTurner) {
        this.turner = turner
    }

    fun unregister(turner: PageTurner) {
        if (this.turner === turner) this.turner = null
    }

    /** Returns true when the event was consumed. Both DOWN and UP are consumed so the volume panel never appears. */
    fun dispatch(event: KeyEvent): Boolean {
        val current = turner ?: return false
        val forward = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_PAGE_DOWN -> true
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_PAGE_UP -> false
            else -> return false
        }
        if (event.action == KeyEvent.ACTION_DOWN) current.turn(forward)
        return true
    }
}

val LocalKeyEventDispatcher = staticCompositionLocalOf<KeyEventDispatcher> {
    error("KeyEventDispatcher not provided")
}
