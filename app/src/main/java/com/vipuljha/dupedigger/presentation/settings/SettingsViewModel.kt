package com.vipuljha.dupedigger.presentation.settings

import androidx.lifecycle.viewModelScope
import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.core.mvi.MviViewModel
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.domain.repository.ExcludedPathRepository
import com.vipuljha.dupedigger.domain.repository.ScanFolderRepository
import com.vipuljha.dupedigger.domain.repository.SettingsRepository
import com.vipuljha.dupedigger.domain.usecase.AddScanFolderUseCase
import com.vipuljha.dupedigger.domain.usecase.ExcludeFolderUseCase
import com.vipuljha.dupedigger.domain.usecase.RemoveExcludedPathUseCase
import com.vipuljha.dupedigger.domain.usecase.RemoveScanFolderUseCase
import com.vipuljha.dupedigger.domain.usecase.SetNearThresholdUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    scanFolders: ScanFolderRepository,
    excludedPaths: ExcludedPathRepository,
    private val addScanFolder: AddScanFolderUseCase,
    private val removeScanFolder: RemoveScanFolderUseCase,
    private val excludeFolder: ExcludeFolderUseCase,
    private val removeExcludedPath: RemoveExcludedPathUseCase,
    settings: SettingsRepository,
    private val setNearThreshold: SetNearThresholdUseCase,
) : MviViewModel<SettingsState, SettingsIntent, SettingsEffect>(SettingsState()) {

    init {
        combine(scanFolders.folders, excludedPaths.paths) { folders, excluded ->
            setState {
                copy(
                    folders = folders.map { it.toUiModel() }.toImmutableList(),
                    excluded = excluded.map(::excludedPathToUiModel).toImmutableList(),
                    isLoading = false,
                )
            }
        }.launchIn(viewModelScope)
        settings.settings
            .map { it.nearHammingThreshold }
            .distinctUntilChanged()
            .onEach { bits -> setState { copy(nearThreshold = bits) } }
            .launchIn(viewModelScope)
    }

    override fun handleIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.AddFolderClicked -> sendEffect(SettingsEffect.PickFolder(intent.purpose))
            is SettingsIntent.FolderPicked -> intent.treeUri?.let { onPicked(intent.purpose, it) }
            is SettingsIntent.RemoveClicked -> onRemove(intent.purpose, intent.key)
            is SettingsIntent.NearThresholdChanged -> viewModelScope.launch { setNearThreshold(intent.bits) }
        }
    }

    private fun onPicked(purpose: FolderPurpose, treeUri: String) {
        viewModelScope.launch {
            val result = when (purpose) {
                FolderPurpose.Scan -> addScanFolder(treeUri)
                FolderPurpose.Exclude -> excludeFolder(treeUri)
            }
            if (result is Outcome.Failure) {
                val message = when (purpose) {
                    FolderPurpose.Scan -> R.string.settings_folder_add_failed
                    FolderPurpose.Exclude -> R.string.settings_exclude_failed
                }
                sendEffect(SettingsEffect.ShowMessage(UiText.res(message)))
            }
        }
    }

    private fun onRemove(purpose: FolderPurpose, key: String) {
        viewModelScope.launch {
            when (purpose) {
                FolderPurpose.Scan -> removeScanFolder(key)
                FolderPurpose.Exclude -> removeExcludedPath(key)
            }
        }
    }
}
