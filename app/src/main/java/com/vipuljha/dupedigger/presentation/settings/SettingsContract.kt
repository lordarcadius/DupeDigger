package com.vipuljha.dupedigger.presentation.settings

import androidx.compose.runtime.Immutable
import com.vipuljha.dupedigger.domain.model.AppSettings
import com.vipuljha.dupedigger.core.mvi.UiEffect
import com.vipuljha.dupedigger.core.mvi.UiIntent
import com.vipuljha.dupedigger.core.mvi.UiState
import com.vipuljha.dupedigger.core.ui.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class SettingsState(
    val folders: ImmutableList<FolderUiModel> = persistentListOf(),
    val excluded: ImmutableList<FolderUiModel> = persistentListOf(),
    val isLoading: Boolean = true,
    val nearThreshold: Int = AppSettings().nearHammingThreshold,
) : UiState

enum class FolderPurpose { Scan, Exclude }

sealed interface SettingsIntent : UiIntent {
    data class AddFolderClicked(val purpose: FolderPurpose) : SettingsIntent

    /** [treeUri] is null when the user backed out of the picker. */
    data class FolderPicked(val purpose: FolderPurpose, val treeUri: String?) : SettingsIntent
    data class RemoveClicked(val purpose: FolderPurpose, val key: String) : SettingsIntent
    data class NearThresholdChanged(val bits: Int) : SettingsIntent
}

sealed interface SettingsEffect : UiEffect {
    data class PickFolder(val purpose: FolderPurpose) : SettingsEffect
    data class ShowMessage(val message: UiText) : SettingsEffect
}
