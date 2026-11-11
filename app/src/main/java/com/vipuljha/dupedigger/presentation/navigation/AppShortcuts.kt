package com.vipuljha.dupedigger.presentation.navigation

import android.view.KeyEvent

/** [OpenScan] comes from the scan notification, not from a key. */
enum class AppShortcut { Home, Back, Close, OpenScan }

/** Ctrl+H goes Home, Alt+Left goes back, Esc closes the innermost screen; null for any other key. */
fun appShortcutFor(
    keyCode: Int,
    ctrl: Boolean,
    alt: Boolean,
    shift: Boolean,
    meta: Boolean
): AppShortcut? {
    if (shift || meta) return null
    return when {
        keyCode == KeyEvent.KEYCODE_H && ctrl && !alt -> AppShortcut.Home
        keyCode == KeyEvent.KEYCODE_DPAD_LEFT && alt && !ctrl -> AppShortcut.Back
        keyCode == KeyEvent.KEYCODE_ESCAPE && !ctrl && !alt -> AppShortcut.Close
        else -> null
    }
}

/** Window-level shortcut hand-off: the activity passes keys and intents in, the navigation host registers [handler]. */
class AppShortcuts {
    private var pending: AppShortcut? = null

    var handler: ((AppShortcut) -> Unit)? = null
        set(value) {
            field = value
            if (value != null) pending?.let { pending = null; value(it) }
        }

    /** Runs [shortcut] now, or as soon as the navigation host is up, as when a notification tap starts the app. */
    fun request(shortcut: AppShortcut) {
        handler?.invoke(shortcut) ?: run { pending = shortcut }
    }

    /** Consumes both halves of a shortcut so the key up can't reach the system as a stray Back. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        val handle = handler ?: return false
        val shortcut = appShortcutFor(
            event.keyCode,
            event.isCtrlPressed,
            event.isAltPressed,
            event.isShiftPressed,
            event.isMetaPressed
        )
            ?: return false
        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) handle(shortcut)
        return true
    }
}
