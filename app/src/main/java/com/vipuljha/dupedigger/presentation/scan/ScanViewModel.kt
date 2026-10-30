package com.vipuljha.dupedigger.presentation.scan

import androidx.lifecycle.viewModelScope
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.core.mvi.MviViewModel
import com.vipuljha.dupedigger.core.ui.toUiText
import com.vipuljha.dupedigger.domain.model.ScanProgress
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.ScanStage
import com.vipuljha.dupedigger.domain.model.ScanStatus
import com.vipuljha.dupedigger.domain.model.kind
import com.vipuljha.dupedigger.domain.repository.ScanRunRepository
import com.vipuljha.dupedigger.domain.repository.ScanScheduler
import com.vipuljha.dupedigger.domain.repository.SettingsRepository
import com.vipuljha.dupedigger.domain.usecase.CancelScanUseCase
import com.vipuljha.dupedigger.domain.usecase.GetAvailableScopesUseCase
import com.vipuljha.dupedigger.domain.usecase.SetNearDuplicatesEnabledUseCase
import com.vipuljha.dupedigger.domain.usecase.SetSemanticEnabledUseCase
import com.vipuljha.dupedigger.domain.usecase.StartScanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Picks a scope and tiers, starts the background scan and follows it.
 * - The phase comes from whether the worker runs and from the latest recorded run, so a scan
 *   too short to be seen running still ends on its result.
 * - Live numbers go out on [progress], apart from [state], so ticks only redraw what reads them.
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    settings: SettingsRepository,
    scanRuns: ScanRunRepository,
    scheduler: ScanScheduler,
    private val getAvailableScopes: GetAvailableScopesUseCase,
    private val startScan: StartScanUseCase,
    private val cancelScan: CancelScanUseCase,
    private val setNearEnabled: SetNearDuplicatesEnabledUseCase,
    private val setAiEnabled: SetSemanticEnabledUseCase,
) : MviViewModel<ScanState, ScanIntent, ScanEffect>(ScanState()) {

    private var available: List<ScanScope> = emptyList()
    private var latestRunId = 0L
    /** Runs newer than this id end on the Done phase; null while no scan is being watched. */
    private var watchedAfterRunId: Long? = null
    private var stageStart: StageStart? = null

    val progress: StateFlow<ScanProgressUi?> = scheduler.observeProgress()
        .map { it?.let { p -> p.toUi(rateFor(p)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    init {
        settings.settings
            .map { it.nearDuplicatesEnabled }
            .distinctUntilChanged()
            .onEach { setState { copy(nearEnabled = it) } }
            .launchIn(viewModelScope)
        settings.settings
            .map { (it.aiModelId != null) to (it.semanticEnabled && it.aiModelId != null) }
            .distinctUntilChanged()
            .onEach { (available, enabled) -> setState { copy(aiAvailable = available, aiEnabled = enabled) } }
            .launchIn(viewModelScope)
        viewModelScope.launch {
            loadScopes()
            combine(scheduler.observeProgress().map { it != null }.distinctUntilChanged(), scanRuns.observeLatest()) { running, latest ->
                latestRunId = latest?.id ?: 0L
                when {
                    running -> {
                        if (watchedAfterRunId == null) watchedAfterRunId = latestRunId - 1
                        setState { copy(phase = ScanPhase.Running, starting = false) }
                    }
                    latest != null && latest.status != ScanStatus.RUNNING && latest.id > (watchedAfterRunId ?: Long.MAX_VALUE) -> {
                        watchedAfterRunId = null
                        setState { copy(phase = ScanPhase.Done(latest.toResult()), starting = false, cancelling = false) }
                    }
                    currentState.phase == ScanPhase.Loading -> setState { copy(phase = ScanPhase.Setup) }
                }
            }.launchIn(this)
        }
    }

    override fun handleIntent(intent: ScanIntent) {
        when (intent) {
            ScanIntent.Refresh -> viewModelScope.launch { loadScopes() }
            is ScanIntent.ScopeSelected -> setState { copy(selectedScope = intent.kind) }
            is ScanIntent.NearToggled -> viewModelScope.launch { setNearEnabled(intent.enabled) }
            is ScanIntent.AiToggled -> viewModelScope.launch { setAiEnabled(intent.enabled) }
            ScanIntent.StartClicked -> start()
            ScanIntent.CancelClicked -> {
                setState { copy(cancelling = true) }
                cancelScan()
            }
            ScanIntent.ScanAgainClicked -> {
                setState { copy(phase = ScanPhase.Setup) }
                viewModelScope.launch { loadScopes() }
            }
            ScanIntent.ViewResultsClicked -> sendEffect(ScanEffect.ViewResults)
        }
    }

    private suspend fun loadScopes() {
        available = getAvailableScopes()
        setState {
            copy(
                scopes = available.map { it.toOption() }.toImmutableList(),
                selectedScope = selectedScope?.takeIf { kind -> available.any { it.kind == kind } } ?: available.firstOrNull()?.kind,
            )
        }
    }

    private fun start() {
        val scope = available.firstOrNull { it.kind == currentState.selectedScope } ?: return
        if (currentState.starting) return
        setState { copy(starting = true) }
        watchedAfterRunId = latestRunId
        viewModelScope.launch {
            val outcome = startScan(scope)
            if (outcome is Outcome.Failure) {
                watchedAfterRunId = null
                setState { copy(starting = false) }
                sendEffect(ScanEffect.ShowMessage(outcome.error.toUiText()))
            }
        }
    }

    private fun rateFor(progress: ScanProgress): Int? {
        val now = System.nanoTime()
        val start = stageStart?.takeIf { it.stage == progress.stage } ?: StageStart(progress.stage, now, progress.done).also { stageStart = it }
        return filesPerSecond(progress.done - start.done, now - start.nanos)
    }

    private class StageStart(val stage: ScanStage, val nanos: Long, val done: Int)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
