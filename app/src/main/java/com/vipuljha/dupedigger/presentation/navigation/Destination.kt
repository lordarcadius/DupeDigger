package com.vipuljha.dupedigger.presentation.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Destination : NavKey {
    @Serializable data object Home : Destination
    @Serializable data object Results : Destination
    @Serializable data object Quarantine : Destination
    @Serializable data object Settings : Destination
    @Serializable data object Scan : Destination
}
