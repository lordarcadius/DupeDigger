package com.vipuljha.dupedigger.presentation.home

import androidx.lifecycle.viewModelScope
import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.mvi.MviViewModel
import com.vipuljha.dupedigger.core.ui.ContentState
import com.vipuljha.dupedigger.core.ui.reloading
import com.vipuljha.dupedigger.core.ui.toUiText
import com.vipuljha.dupedigger.domain.repository.DuplicateGroupRepository
import com.vipuljha.dupedigger.domain.repository.ScanRunRepository
import com.vipuljha.dupedigger.domain.repository.ScanScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * Home: what the saved groups could free, the last scan, and the way into the Scan screen.
 * - The summary follows the database and the scan worker live.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val groups: DuplicateGroupRepository,
    private val scanRuns: ScanRunRepository,
    private val scheduler: ScanScheduler,
) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {

    private var summaryJob: Job? = null

    init {
        observeSummary()
    }

    override fun handleIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Retry -> {
                setState { copy(content = content.reloading()) }
                observeSummary()
            }
            HomeIntent.OpenScanClicked -> sendEffect(HomeEffect.OpenScan)
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
}
