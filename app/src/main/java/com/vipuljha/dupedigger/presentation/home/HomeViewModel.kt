package com.vipuljha.dupedigger.presentation.home

import androidx.lifecycle.viewModelScope
import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.mvi.MviViewModel
import com.vipuljha.dupedigger.core.ui.ContentState
import com.vipuljha.dupedigger.core.ui.reloading
import com.vipuljha.dupedigger.core.ui.toUiText
import com.vipuljha.dupedigger.domain.repository.DeviceStorage
import com.vipuljha.dupedigger.domain.repository.DuplicateGroupRepository
import com.vipuljha.dupedigger.domain.repository.ScanRunRepository
import com.vipuljha.dupedigger.domain.repository.ScanScheduler
import com.vipuljha.dupedigger.presentation.scan.ScanProgressUi
import com.vipuljha.dupedigger.presentation.scan.toUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Home: what the saved groups could free, the last scan, and the way into the Scan screen.
 * - The summary follows the database and the scan worker live.
 * - A running scan's numbers go out on [progress], apart from [state], so ticks only redraw what reads them; they come
 *   from WorkManager, so they reappear when the app is reopened, even after the process was killed.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val groups: DuplicateGroupRepository,
    private val scanRuns: ScanRunRepository,
    private val scheduler: ScanScheduler,
    private val deviceStorage: DeviceStorage,
) : MviViewModel<HomeState, HomeIntent, HomeEffect>(HomeState()) {

    private var summaryJob: Job? = null

    val progress: StateFlow<ScanProgressUi?> = scheduler.observeProgress()
        .map { it?.toUi(rate = null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

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
            ::Triple,
        )
            // Space on the volumes is read again on each change, so a delete shows up in the meter at once.
            .map { (kinds, lastRun, scanning) ->
                ContentState.Success(
                    homeUiModel(
                        kinds,
                        lastRun,
                        scanning,
                        deviceStorage.usage(),
                        System.currentTimeMillis()
                    ),
                ) as ContentState<HomeUiModel>
            }
            .catch { emit(ContentState.Error(DomainError.Unknown.toUiText())) }
            .onEach { setState { copy(content = it) } }
            .launchIn(viewModelScope)
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
