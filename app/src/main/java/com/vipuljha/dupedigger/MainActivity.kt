package com.vipuljha.dupedigger

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.vipuljha.dupedigger.core.designsystem.DupeDiggerTheme
import com.vipuljha.dupedigger.presentation.navigation.AppEntry
import com.vipuljha.dupedigger.presentation.navigation.AppShortcuts
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val shortcuts = AppShortcuts()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            DupeDiggerTheme {
                AppEntry(shortcuts)
            }
        }
    }

    // androidx marks its own override restricted; this overrides the public platform Activity method.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = shortcuts.onKeyEvent(event) || super.dispatchKeyEvent(event)
}
