package com.vipuljha.dupedigger.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.vipuljha.dupedigger.R

enum class TopLevelTab(val root: Destination, @param:StringRes val label: Int, val icon: ImageVector) {
    Home(Destination.Home, R.string.tab_home, Icons.Filled.Home),
    Results(Destination.Results, R.string.tab_results, Icons.Filled.ContentCopy),
    Quarantine(Destination.Quarantine, R.string.tab_quarantine, Icons.Filled.Inventory2),
    Settings(Destination.Settings, R.string.tab_settings, Icons.Filled.Settings),
}
