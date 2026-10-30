package com.vipuljha.dupedigger.presentation.home

import androidx.lifecycle.viewModelScope
import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.core.mvi.MviViewModel
import com.vipuljha.dupedigger.core.ui.ContentState
import com.vipuljha.dupedigger.core.ui.reloading
import com.vipuljha.dupedigger.core.ui.toUiText
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.repository.DuplicateGroupRepository
import com.vipuljha.dupedigger.domain.repository.ScanRunRepository
import com.vipuljha.dupedigger.domain.repository.ScanScheduler
import com.vipuljha.dupedigger.domain.usecase.AddScanFolderUseCase
import com.vipuljha.dupedigger.domain.usecase.GetAvailableScopesUseCase
import com.vipuljha.dupedigger.domain.usecase.StartScanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Home: what the saved groups could free, the last scan, and quick ways to start one.
 * - The summary follows the database and the scan worker live.
 * - Quick actions follow the current access, read again on every resume.
 * - A started scan opens the Scan screen to show its progress.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val groups: DuplicateGroupRepository,
    private val scanRuns: ScanRunRepository,
    private val scheduler: ScanScheduler,
    private val getAvailableScopes: GetAvailableScopesUseCase,
    private val startScan: StartScanUseCase,
    private val addScanFolder: AddScanFolderUseCase,
) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {

    private var summaryJob: Job? = null

    init {
        observeSummary()
        refreshActions()
    }

    override fun handleIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Refresh -> refreshActions()
            HomeIntent.Retry -> {
                setState { copy(content = content.reloading()) }
                observeSummary()
            }
            HomeIntent.OpenScanClicked -> sendEffect(HomeEffect.OpenScan)
            is HomeIntent.QuickActionClicked -> when (intent.action) {
                QuickAction.Everything -> start(QuickAction.Everything) { ScanScope.WholeDevice }
                QuickAction.Media -> start(QuickAction.Media) { ScanScope.MediaOnly }
                QuickAction.Folder -> sendEffect(HomeEffect.PickFolder)
            }
            is HomeIntent.FolderPicked -> intent.treeUri?.let(::scanPickedFolders)
        }
    }

    private fun observeSummary() {
        summaryJob?.cancel()
        summaryJob = combine(
            groups.observeReclaimable(),
            scanRuns.observeLatest(),
            scheduler.observeProgress().map { it != null }.distinctUntilChanged(),
        ) { kinds, lastRun, scanning ->
            ContentState.Success(homeUiModel(kinds, lastRun, scanning, System.currentTimeMillis())) as ContentState<HomeUiModel>
        }
            .catch { emit(ContentState.Error(DomainError.Unknown.toUiText())) }
            .onEach { setState { copy(content = it) } }
            .launchIn(viewModelScope)
    }

    private fun refreshActions() {
        viewModelScope.launch {
            val available = getAvailableScopes()
            setState { copy(quickActions = quickActions(available)) }
        }
    }

    private fun scanPickedFolders(treeUri: String) = start(QuickAction.Folder) {
        if (addScanFolder(treeUri) is Outcome.Failure) null else getAvailableScopes().filterIsInstance<ScanScope.Folders>().firstOrNull()
    }

    /** [scope] returning null means it couldn't be prepared; the folder flow then reports it. */
    private fun start(action: QuickAction, scope: suspend () -> ScanScope?) {
        if (currentState.startingAction != null) return
        setState { copy(startingAction = action) }
        viewModelScope.launch {
            val outcome = scope()?.let { startScan(it) } ?: Outcome.Failure(DomainError.PermissionDenied)
            setState { copy(startingAction = null) }
            when (outcome) {
                is Outcome.Success -> sendEffect(HomeEffect.OpenScan)
                is Outcome.Failure -> sendEffect(HomeEffect.ShowMessage(outcome.error.toUiText()))
            }
            refreshActions()
        }
    }
}
