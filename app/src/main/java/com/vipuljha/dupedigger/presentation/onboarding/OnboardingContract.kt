package com.vipuljha.dupedigger.presentation.onboarding

import androidx.compose.runtime.Immutable
import com.vipuljha.dupedigger.core.mvi.UiEffect
import com.vipuljha.dupedigger.core.mvi.UiIntent
import com.vipuljha.dupedigger.core.mvi.UiState
import com.vipuljha.dupedigger.core.ui.UiText
import kotlinx.collections.immutable.ImmutableList

enum class MediaAction { Allow, AllowAll, OpenSettings, None }

@Immutable
data class OnboardingState(
    val mediaStatus: UiText? = null,
    val mediaAction: MediaAction = MediaAction.None,
    val mediaBlocked: Boolean = false,
    val allFilesStatus: UiText? = null,
    val allFilesActionVisible: Boolean = false,
    val isFinishing: Boolean = false,
) : UiState

sealed interface OnboardingIntent : UiIntent {
    data object Refresh : OnboardingIntent
    data object MediaActionClicked : OnboardingIntent

    /** [canAskAgain] is true when any denied permission can still show the system prompt. */
    data class MediaPermissionResult(val canAskAgain: Boolean) : OnboardingIntent
    data object AllFilesClicked : OnboardingIntent
    data object ContinueClicked : OnboardingIntent
}

sealed interface OnboardingEffect : UiEffect {
    data class RequestMediaPermissions(val permissions: ImmutableList<String>) : OnboardingEffect
    data object OpenAllFilesAccessSettings : OnboardingEffect
    data object OpenAppSettings : OnboardingEffect
}
