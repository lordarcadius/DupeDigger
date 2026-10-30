package com.vipuljha.dupedigger.presentation.navigation

import android.view.KeyEvent

enum class AppShortcut { Home, Back, Close }

/** Ctrl+H goes Home, Alt+Left goes back, Esc closes the innermost screen; null for any other key. */
fun appShortcutFor(keyCode: Int, ctrl: Boolean, alt: Boolean, shift: Boolean, meta: Boolean): AppShortcut? {
    if (shift || meta) return null
    return when {
        keyCode == KeyEvent.KEYCODE_H && ctrl && !alt -> AppShortcut.Home
        keyCode == KeyEvent.KEYCODE_DPAD_LEFT && alt && !ctrl -> AppShortcut.Back
        keyCode == KeyEvent.KEYCODE_ESCAPE && !ctrl && !alt -> AppShortcut.Close
        else -> null
    }
}

/** Window-level shortcut hand-off: the activity passes keys in, the navigation host registers [handler]. */
class AppShortcuts {
    var handler: ((AppShortcut) -> Unit)? = null

    /** Consumes both halves of a shortcut so the key up can't reach the system as a stray Back. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        val handle = handler ?: return false
        val shortcut = appShortcutFor(event.keyCode, event.isCtrlPressed, event.isAltPressed, event.isShiftPressed, event.isMetaPressed)
            ?: return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) handle(shortcut)
        return true
    }
}
