package com.vipuljha.dupedigger.presentation.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vipuljha.dupedigger.core.designsystem.AppTopBar
import com.vipuljha.dupedigger.core.designsystem.TopBarTitle

/** Stand-in for a top-level screen until its feature lands; it shows only the top bar. */
@Composable
fun PlaceholderScreen(title: String, onMenuClick: (() -> Unit)?, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { AppTopBar(onMenuClick = onMenuClick) { TopBarTitle(title) } },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding))
    }
}
