package com.vipuljha.dupedigger.presentation.ai

import androidx.lifecycle.viewModelScope
import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.mvi.MviViewModel
import com.vipuljha.dupedigger.core.ui.ContentState
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.core.ui.toUiText
import com.vipuljha.dupedigger.domain.model.ModelCatalog
import com.vipuljha.dupedigger.domain.repository.ModelDownloads
import com.vipuljha.dupedigger.domain.repository.SettingsRepository
import com.vipuljha.dupedigger.domain.usecase.CancelModelDownloadUseCase
import com.vipuljha.dupedigger.domain.usecase.ChooseModelUseCase
import com.vipuljha.dupedigger.domain.usecase.DeleteModelUseCase
import com.vipuljha.dupedigger.domain.usecase.DownloadModelUseCase
import com.vipuljha.dupedigger.domain.usecase.SetAiBackendUseCase
import com.vipuljha.dupedigger.domain.usecase.SetAiChargingOnlyUseCase
import com.vipuljha.dupedigger.domain.usecase.SetModelWifiOnlyUseCase
import com.vipuljha.dupedigger.domain.usecase.SetSemanticEnabledUseCase
import com.vipuljha.dupedigger.domain.usecase.SetSemanticThresholdUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The AI tier: models to download, choose and delete, and the options that shape how it runs. */
@HiltViewModel
class AiViewModel @Inject constructor(
    settings: SettingsRepository,
    downloads: ModelDownloads,
    private val download: DownloadModelUseCase,
    private val cancelDownload: CancelModelDownloadUseCase,
    private val chooseModel: ChooseModelUseCase,
    private val deleteModel: DeleteModelUseCase,
    private val setEnabled: SetSemanticEnabledUseCase,
    private val setBackend: SetAiBackendUseCase,
    private val setThreshold: SetSemanticThresholdUseCase,
    private val setWifiOnly: SetModelWifiOnlyUseCase,
    private val setChargingOnly: SetAiChargingOnlyUseCase,
) : MviViewModel<AiState, AiIntent, AiEffect>(AiState()) {

    init {
        val states = combine(ModelCatalog.variants.map { variant -> downloads.observe(variant) }) { it.toList() }
        combine(settings.settings, states) { appSettings, modelStates -> aiUi(appSettings, ModelCatalog.variants.zip(modelStates)) }
            .onEach { ui -> setState { copy(content = ContentState.Success(ui)) } }
            .catch { setState { copy(content = ContentState.Error(DomainError.Unknown.toUiText(), retryable = false)) } }
            .launchIn(viewModelScope)
    }

    override fun handleIntent(intent: AiIntent) {
        val variant = { id: String -> ModelCatalog.byId(id) }
        when (intent) {
            is AiIntent.EnabledToggled -> viewModelScope.launch { setEnabled(intent.enabled) }
            is AiIntent.DownloadClicked -> variant(intent.id)?.let { viewModelScope.launch { download(it) } }
            is AiIntent.CancelClicked -> variant(intent.id)?.let(cancelDownload::invoke)
            is AiIntent.UseClicked -> variant(intent.id)?.let { v ->
                viewModelScope.launch { if (!chooseModel(v)) sendEffect(AiEffect.ShowMessage(UiText.res(R.string.ai_not_downloaded))) }
            }
            is AiIntent.DeleteClicked -> rowOf(intent.id)?.let { row -> setState { copy(confirmDelete = row) } }
            AiIntent.DeleteDismissed -> setState { copy(confirmDelete = null) }
            AiIntent.DeleteConfirmed -> {
                val row = currentState.confirmDelete ?: return
                setState { copy(confirmDelete = null) }
                variant(row.id)?.let { v ->
                    viewModelScope.launch {
                        deleteModel(v)
                        sendEffect(AiEffect.ShowMessage(UiText.res(R.string.ai_deleted, row.title)))
                    }
                }
            }
            is AiIntent.BackendSelected -> viewModelScope.launch { setBackend(intent.backend) }
            is AiIntent.ThresholdChanged -> viewModelScope.launch { setThreshold(intent.threshold) }
            is AiIntent.WifiOnlyToggled -> viewModelScope.launch { setWifiOnly(intent.wifiOnly) }
            is AiIntent.ChargingOnlyToggled -> viewModelScope.launch { setChargingOnly(intent.chargingOnly) }
        }
    }

    private fun rowOf(id: String) = (currentState.content as? ContentState.Success)?.data?.models?.firstOrNull { it.id == id }
}
