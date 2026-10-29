package com.vipuljha.dupedigger.presentation.navigation

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppShortcutTest {
    private fun shortcut(keyCode: Int, ctrl: Boolean = false, alt: Boolean = false, shift: Boolean = false, meta: Boolean = false) =
        appShortcutFor(keyCode, ctrl, alt, shift, meta)

    @Test
    fun `the house shortcuts map to their actions`() {
        assertEquals(AppShortcut.Home, shortcut(KeyEvent.KEYCODE_H, ctrl = true))
        assertEquals(AppShortcut.Back, shortcut(KeyEvent.KEYCODE_DPAD_LEFT, alt = true))
        assertEquals(AppShortcut.Close, shortcut(KeyEvent.KEYCODE_ESCAPE))
    }

    @Test
    fun `plain keys and other modifier mixes are left alone`() {
        assertNull(shortcut(KeyEvent.KEYCODE_H))
        assertNull(shortcut(KeyEvent.KEYCODE_DPAD_LEFT))
        assertNull(shortcut(KeyEvent.KEYCODE_H, ctrl = true, shift = true))
        assertNull(shortcut(KeyEvent.KEYCODE_H, ctrl = true, alt = true))
        assertNull(shortcut(KeyEvent.KEYCODE_DPAD_LEFT, alt = true, ctrl = true))
        assertNull(shortcut(KeyEvent.KEYCODE_ESCAPE, ctrl = true))
        assertNull(shortcut(KeyEvent.KEYCODE_ESCAPE, meta = true))
        assertNull(shortcut(KeyEvent.KEYCODE_F, ctrl = true))
    }
}
