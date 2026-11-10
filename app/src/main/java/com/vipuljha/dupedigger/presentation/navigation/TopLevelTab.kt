package com.vipuljha.dupedigger.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileCopy
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.vipuljha.dupedigger.R

/**
 * The top-level destinations. Each carries both icon forms: M3 fills the active destination's icon
 * and leaves every other one outlined, which is a required signal and not decoration.
 */
enum class TopLevelTab(
    val root: Destination,
    @param:StringRes val label: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Home(Destination.Home, R.string.tab_home, Icons.Outlined.Home, Icons.Filled.Home),
    Results(Destination.Results, R.string.tab_results, Icons.Outlined.FileCopy, Icons.Filled.FileCopy),
    Quarantine(Destination.Quarantine, R.string.tab_quarantine, Icons.Outlined.Inventory2, Icons.Filled.Inventory2),
    Settings(Destination.Settings, R.string.tab_settings, Icons.Outlined.Settings, Icons.Filled.Settings),
}
