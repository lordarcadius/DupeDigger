package com.vipuljha.dupedigger.presentation.scan

import androidx.lifecycle.viewModelScope
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.core.mvi.MviViewModel
import com.vipuljha.dupedigger.core.ui.toUiText
import com.vipuljha.dupedigger.domain.model.AllMediaKinds
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.ScanFolder
import com.vipuljha.dupedigger.domain.model.ScanPreset
import com.vipuljha.dupedigger.domain.model.ScanProgress
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.ScanStage
import com.vipuljha.dupedigger.domain.model.ScanStatus
import com.vipuljha.dupedigger.domain.model.ScopeKind
import com.vipuljha.dupedigger.domain.model.kind
import com.vipuljha.dupedigger.domain.repository.ScanRunRepository
import com.vipuljha.dupedigger.domain.repository.ScanScheduler
import com.vipuljha.dupedigger.domain.repository.SemanticStageSkip
import com.vipuljha.dupedigger.domain.repository.SettingsRepository
import com.vipuljha.dupedigger.domain.usecase.AddScanFolderUseCase
import com.vipuljha.dupedigger.domain.usecase.CancelScanUseCase
import com.vipuljha.dupedigger.domain.usecase.DeleteScanPresetUseCase
import com.vipuljha.dupedigger.domain.usecase.GetAvailableScopesUseCase
import com.vipuljha.dupedigger.domain.usecase.ObserveScanPresetsUseCase
import com.vipuljha.dupedigger.domain.usecase.SaveScanPresetUseCase
import com.vipuljha.dupedigger.domain.usecase.SetNearDuplicatesEnabledUseCase
import com.vipuljha.dupedigger.domain.usecase.SetSemanticEnabledUseCase
import com.vipuljha.dupedigger.domain.usecase.SkipSemanticStageUseCase
import com.vipuljha.dupedigger.domain.usecase.StartScanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
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
 * Sets up a scan, starts it in the background and follows it.
 * - Setup offers presets first; with none it asks for file types, then whole storage or picked folders.
 * - The same two steps create or edit a preset, ending in Save instead of Start.
 * - The phase comes from whether the worker runs and from the latest recorded run, so a scan
 *   too short to be seen running still ends on its result.
 * - Live numbers go out on [progress], apart from [state], so ticks only redraw what reads them.
 */
@HiltViewModel
class ScanViewModel @Inject constructor(
    settings: SettingsRepository,
    scanRuns: ScanRunRepository,
    scheduler: ScanScheduler,
    observePresets: ObserveScanPresetsUseCase,
    private val getAvailableScopes: GetAvailableScopesUseCase,
    private val startScan: StartScanUseCase,
    private val cancelScan: CancelScanUseCase,
    private val setNearEnabled: SetNearDuplicatesEnabledUseCase,
    private val setAiEnabled: SetSemanticEnabledUseCase,
    private val savePreset: SaveScanPresetUseCase,
    private val deletePreset: DeleteScanPresetUseCase,
    private val addScanFolder: AddScanFolderUseCase,
    private val skipSemanticStage: SkipSemanticStageUseCase,
    semanticStageSkip: SemanticStageSkip,
) : MviViewModel<ScanState, ScanIntent, ScanEffect>(ScanState(kinds = kindOptions(AllMediaKinds))) {

    private var available: List<ScanScope> = emptyList()
    private var presets: List<ScanPreset> = emptyList()
    /** Null until the user changes the folder selection, so every picked folder counts as selected. */
    private var selectedFolders: Set<String>? = null
    private var lastStarted: Pair<ScanScope, Set<MediaKind>>? = null
    private var latestRunId = 0L
    /** Runs newer than this id end on the Done phase; null while no scan is being watched. */
    private var watchedAfterRunId: Long? = null
    private var stageStart: StageStart? = null

    private val picked: List<ScanFolder> get() = available.filterIsInstance<ScanScope.Folders>().flatMap { it.folders }

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
        semanticStageSkip.requested.onEach { setState { copy(aiSkipped = it) } }.launchIn(viewModelScope)
        viewModelScope.launch {
            loadScopes()
            observePresets().onEach {
                presets = it
                showPresets()
            }.launchIn(this)
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
            is ScanIntent.PresetSelected -> setState { copy(selectedPresetId = intent.id) }
            ScanIntent.NextClicked -> next()
            ScanIntent.BackClicked -> back()
            is ScanIntent.KindToggled -> setState {
                copy(kinds = kinds.map { if (it.kind == intent.kind) it.copy(selected = !it.selected) else it }.toImmutableList())
            }
            is ScanIntent.ScopeSelected -> setState { copy(selectedScope = intent.kind) }
            is ScanIntent.FolderToggled -> {
                val current = selectedFolders ?: picked.map { it.uri }.toSet()
                selectedFolders = if (intent.uri in current) current - intent.uri else current + intent.uri
                showFolders()
            }
            ScanIntent.AddFolderClicked -> sendEffect(ScanEffect.PickFolder)
            is ScanIntent.FolderPicked -> intent.treeUri?.let(::addFolder)
            ScanIntent.NewPresetClicked -> editDraft(PresetDraftUi(0, ""), AllMediaKinds, null)
            is ScanIntent.EditPresetClicked -> presets.firstOrNull { it.id == intent.id && it.deletable }?.let {
                editDraft(PresetDraftUi(it.id, it.name), it.kinds, it.scope)
            }
            is ScanIntent.DraftNameChanged -> setState { copy(draft = draft?.copy(name = intent.name)) }
            ScanIntent.SavePresetClicked -> save()
            is ScanIntent.DeletePresetClicked -> setState {
                copy(confirmDelete = this@ScanViewModel.presets.firstOrNull { it.id == intent.id && it.deletable }?.toUi(available))
            }
            ScanIntent.DeleteConfirmed -> delete()
            ScanIntent.DeleteDismissed -> setState { copy(confirmDelete = null) }
            is ScanIntent.NearToggled -> viewModelScope.launch { setNearEnabled(intent.enabled) }
            is ScanIntent.AiToggled -> viewModelScope.launch { setAiEnabled(intent.enabled) }
            ScanIntent.StartClicked -> start()
            ScanIntent.SkipAiClicked -> skipSemanticStage()
            ScanIntent.CancelClicked -> {
                setState { copy(cancelling = true) }
                cancelScan()
            }
            ScanIntent.ScanAgainClicked -> {
                setState { copy(phase = ScanPhase.Setup, step = SetupStep.Presets, draft = null) }
                viewModelScope.launch { loadScopes() }
            }
            ScanIntent.ViewResultsClicked -> sendEffect(ScanEffect.ViewResults)
        }
    }

    private fun next() = setState {
        when (step) {
            SetupStep.Presets -> copy(step = SetupStep.Types, selectedPresetId = null)
            SetupStep.Types -> if (canLeaveTypes) copy(step = SetupStep.Where) else this
            SetupStep.Where -> this
        }
    }

    /** Going back from the file types of a preset being created or edited drops the draft. */
    private fun back() = setState {
        when (step) {
            SetupStep.Presets -> this
            SetupStep.Types -> copy(step = SetupStep.Presets, draft = null, kinds = kindOptions(AllMediaKinds))
            SetupStep.Where -> copy(step = SetupStep.Types)
        }
    }

    private fun editDraft(draft: PresetDraftUi, kinds: Set<MediaKind>, scope: ScanScope?) {
        selectedFolders = (scope as? ScanScope.Folders)?.folders?.map { it.uri }?.toSet()
        setState { copy(step = SetupStep.Types, draft = draft, kinds = kindOptions(kinds), selectedScope = scope?.kind ?: selectedScope) }
        showFolders()
    }

    private suspend fun loadScopes() {
        available = getAvailableScopes()
        val options = whereOptions(available, picked)
        setState {
            copy(scopes = options, selectedScope = selectedScope?.takeIf { kind -> options.any { it.kind == kind } } ?: options.first().kind)
        }
        showFolders()
        showPresets()
    }

    private fun showPresets() = setState { copy(presets = this@ScanViewModel.presets.map { it.toUi(available) }.toImmutableList()) }

    private fun showFolders() {
        val chosen = selectedFolders
        setState { copy(folders = picked.map { FolderOptionUi(it.uri, it.name, chosen == null || it.uri in chosen) }.toImmutableList()) }
    }

    /** A newly picked folder comes back selected, with picked folders chosen as where to look. */
    private fun addFolder(treeUri: String) {
        viewModelScope.launch {
            val before = picked.map { it.uri }.toSet()
            val outcome = addScanFolder(treeUri)
            if (outcome is Outcome.Failure) {
                sendEffect(ScanEffect.ShowMessage(outcome.error.toUiText()))
                return@launch
            }
            available = getAvailableScopes()
            selectedFolders = selectedFolders?.plus(picked.map { it.uri }.toSet() - before)
            setState { copy(scopes = whereOptions(available, picked), selectedScope = ScopeKind.FOLDERS) }
            showFolders()
            showPresets()
        }
    }

    /** The scope and kinds the file-type and where steps describe; null while they don't describe one. */
    private fun chosen(): Pair<ScanScope, Set<MediaKind>>? {
        val state = currentState
        val kinds = state.kinds.filter { it.selected }.map { it.kind }.toSet()
        val scope = when (state.selectedScope) {
            ScopeKind.WHOLE_DEVICE -> ScanScope.WholeDevice
            ScopeKind.MEDIA_ONLY -> ScanScope.MediaOnly
            ScopeKind.FOLDERS -> ScanScope.Folders(picked.filter { folder -> state.folders.any { it.uri == folder.uri && it.selected } }.toImmutableList())
                .takeIf { it.folders.isNotEmpty() }
            ScopeKind.PATHS, null -> null
        }
        return if (scope == null || kinds.isEmpty()) null else scope to kinds
    }

    /** Retry repeats the last scan, or goes back to setup when this screen never started one (as after process death). */
    private fun start() {
        val state = currentState
        val request = when {
            state.phase is ScanPhase.Done -> lastStarted ?: return handleIntent(ScanIntent.ScanAgainClicked)
            state.step == SetupStep.Presets -> presets.firstOrNull { it.id == state.selectedPresetId }?.let { it.scope to it.kinds }
            state.step == SetupStep.Where && state.draft == null -> chosen()
            else -> null
        }
        if (request == null || state.starting) return
        lastStarted = request
        setState { copy(starting = true) }
        watchedAfterRunId = latestRunId
        viewModelScope.launch {
            val outcome = startScan(request.first, request.second)
            if (outcome is Outcome.Failure) {
                watchedAfterRunId = null
                setState { copy(starting = false) }
                sendEffect(ScanEffect.ShowMessage(outcome.error.toUiText()))
            }
        }
    }

    private fun save() {
        val draft = currentState.draft ?: return
        val (scope, kinds) = chosen() ?: return
        if (currentState.saving) return
        setState { copy(saving = true) }
        viewModelScope.launch {
            when (val outcome = savePreset(ScanPreset(draft.id, draft.name, scope, kinds.toImmutableSet()))) {
                is Outcome.Success -> setState {
                    copy(saving = false, draft = null, step = SetupStep.Presets, selectedPresetId = outcome.data, kinds = kindOptions(AllMediaKinds))
                }
                is Outcome.Failure -> {
                    setState { copy(saving = false) }
                    sendEffect(ScanEffect.ShowMessage(outcome.error.toUiText()))
                }
            }
        }
    }

    private fun delete() {
        val target = presets.firstOrNull { it.id == currentState.confirmDelete?.id }
        setState { copy(confirmDelete = null) }
        if (target == null) return
        viewModelScope.launch {
            when (val outcome = deletePreset(target)) {
                is Outcome.Success -> setState { if (selectedPresetId == target.id) copy(selectedPresetId = null) else this }
                is Outcome.Failure -> sendEffect(ScanEffect.ShowMessage(outcome.error.toUiText()))
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
