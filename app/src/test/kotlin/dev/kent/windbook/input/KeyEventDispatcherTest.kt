package dev.kent.windbook.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KeyEventDispatcherTest {
    private val turns = mutableListOf<Boolean>()
    private val turner = KeyEventDispatcher.PageTurner { forward -> turns += forward; true }

    @Test fun volumeKeysTurnPagesOnlyWhenRegistered() {
        val dispatcher = KeyEventDispatcher()
        assertFalse(dispatcher.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN)))

        dispatcher.register(turner)
        assertTrue(dispatcher.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_DOWN)))
        assertTrue(dispatcher.dispatch(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_DOWN)))
        assertTrue(dispatcher.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP)))

        assertEquals(listOf(true, false), turns) // UP events consumed but don't turn
    }

    @Test fun otherKeysPassThrough() {
        val dispatcher = KeyEventDispatcher().apply { register(turner) }
        assertFalse(dispatcher.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_A)))
    }

    @Test fun unregisterOnlyRemovesSameTurner() {
        val dispatcher = KeyEventDispatcher().apply { register(turner) }
        dispatcher.unregister(KeyEventDispatcher.PageTurner { true })
        assertTrue(dispatcher.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_DOWN)))
        dispatcher.unregister(turner)
        assertFalse(dispatcher.dispatch(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PAGE_DOWN)))
    }
}
