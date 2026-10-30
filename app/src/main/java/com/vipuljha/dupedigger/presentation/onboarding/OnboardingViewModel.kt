package com.vipuljha.dupedigger.presentation.onboarding

import androidx.lifecycle.viewModelScope
import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.mvi.MviViewModel
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.domain.model.MediaAccess
import com.vipuljha.dupedigger.domain.model.StorageAccessState
import com.vipuljha.dupedigger.domain.repository.StorageAccess
import com.vipuljha.dupedigger.domain.usecase.CompleteOnboardingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Explains and requests storage access.
 * - Media permissions are requested by API level; a denial with no prompt left switches the action to Settings.
 * - All files access is an optional Deep scan granted in system Settings (D-004).
 * - Continue is always allowed, because picked folders work without any permission; the stored flag then swaps in the shell.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val storageAccess: StorageAccess,
    private val completeOnboarding: CompleteOnboardingUseCase,
) : MviViewModel<OnboardingState, OnboardingIntent, OnboardingEffect>(OnboardingState()) {

    init {
        refresh()
    }

    override fun handleIntent(intent: OnboardingIntent) {
        when (intent) {
            OnboardingIntent.Refresh -> refresh()
            OnboardingIntent.MediaActionClicked -> onMediaAction()
            is OnboardingIntent.MediaPermissionResult -> onPermissionResult(intent.canAskAgain)
            OnboardingIntent.AllFilesClicked -> sendEffect(OnboardingEffect.OpenAllFilesAccessSettings)
            OnboardingIntent.ContinueClicked -> finish()
        }
    }

    private fun onMediaAction() = when (currentState.mediaAction) {
        MediaAction.Allow, MediaAction.AllowAll ->
            sendEffect(OnboardingEffect.RequestMediaPermissions(storageAccess.mediaPermissions.toImmutableList()))
        MediaAction.OpenSettings -> sendEffect(OnboardingEffect.OpenAppSettings)
        MediaAction.None -> Unit
    }

    private fun onPermissionResult(canAskAgain: Boolean) {
        val access = storageAccess.current()
        render(access, blocked = access.media == MediaAccess.NONE && !canAskAgain)
    }

    private fun refresh() {
        val access = storageAccess.current()
        render(access, blocked = access.media == MediaAccess.NONE && currentState.mediaBlocked)
    }

    private fun render(access: StorageAccessState, blocked: Boolean) = setState {
        copy(
            mediaBlocked = blocked,
            mediaStatus = UiText.res(
                when {
                    access.media == MediaAccess.FULL -> R.string.onboarding_status_allowed
                    access.media == MediaAccess.PARTIAL -> R.string.onboarding_status_partial
                    blocked -> R.string.onboarding_status_blocked
                    else -> R.string.onboarding_status_not_allowed
                },
            ),
            mediaAction = when {
                access.media == MediaAccess.FULL -> MediaAction.None
                access.media == MediaAccess.PARTIAL -> MediaAction.AllowAll
                blocked -> MediaAction.OpenSettings
                else -> MediaAction.Allow
            },
            allFilesStatus = UiText.res(if (access.allFiles) R.string.onboarding_status_on else R.string.onboarding_status_off),
            allFilesActionVisible = !access.allFiles,
        )
    }

    private fun finish() {
        if (currentState.isFinishing) return
        setState { copy(isFinishing = true) }
        viewModelScope.launch { completeOnboarding() }
    }
}
