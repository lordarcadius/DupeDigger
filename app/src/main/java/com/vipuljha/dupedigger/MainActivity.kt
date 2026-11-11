package com.vipuljha.dupedigger

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vipuljha.dupedigger.core.designsystem.DupeDiggerTheme
import com.vipuljha.dupedigger.data.worker.ScanNotifications
import com.vipuljha.dupedigger.presentation.navigation.AppEntry
import com.vipuljha.dupedigger.presentation.navigation.AppShortcut
import com.vipuljha.dupedigger.presentation.navigation.AppShortcuts
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val shortcuts = AppShortcuts()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handle(intent)
        setContent {
            DupeDiggerTheme {
                AppEntry(shortcuts)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent) {
        if (intent.action == ScanNotifications.ACTION_OPEN_SCAN) shortcuts.request(AppShortcut.OpenScan)
    }

    // androidx marks its own override restricted; this overrides the public platform Activity method.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        shortcuts.onKeyEvent(event) || super.dispatchKeyEvent(event)
}
