package com.vipuljha.dupedigger.presentation.scan

import androidx.compose.runtime.Immutable
import com.vipuljha.dupedigger.core.mvi.UiEffect
import com.vipuljha.dupedigger.core.mvi.UiIntent
import com.vipuljha.dupedigger.core.mvi.UiState
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.ScopeKind
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** Setup picks what to scan; Running follows the worker; Done shows how a scan watched here ended. */
sealed interface ScanPhase {
    data object Loading : ScanPhase
    data object Setup : ScanPhase
    data object Running : ScanPhase
    data class Done(val result: ScanResultUi) : ScanPhase
}

/** Setup asks for a preset first; with none it asks for file types, then where to look. */
enum class SetupStep { Presets, Types, Where }

@Immutable
data class ScanState(
    val phase: ScanPhase = ScanPhase.Loading,
    val step: SetupStep = SetupStep.Presets,
    val presets: ImmutableList<PresetUi> = persistentListOf(),
    /** Null means no preset: file types and where to look are picked on the next steps. */
    val selectedPresetId: Long? = null,
    val kinds: ImmutableList<KindOptionUi> = persistentListOf(),
    val scopes: ImmutableList<ScopeOptionUi> = persistentListOf(),
    val selectedScope: ScopeKind? = null,
    val folders: ImmutableList<FolderOptionUi> = persistentListOf(),
    /** Set while a preset is created (id 0) or edited; the last step then saves it instead of scanning. */
    val draft: PresetDraftUi? = null,
    val confirmDelete: PresetUi? = null,
    val nearEnabled: Boolean = true,
    /** The AI tier can be switched only once a downloaded model is chosen. */
    val aiAvailable: Boolean = false,
    val aiEnabled: Boolean = false,
    val starting: Boolean = false,
    val saving: Boolean = false,
    val cancelling: Boolean = false,
    /** A skip of the running scan's AI stage was asked for. */
    val aiSkipped: Boolean = false,
) : UiState {
    val canStartPreset: Boolean get() = presets.firstOrNull { it.id == selectedPresetId }?.enabled == true

    /** What a scan started from the first step with no preset would cover; null while the scopes are still loading. */
    val defaultScopeTitle: UiText? get() = scopes.firstOrNull { it.kind == selectedScope }?.title
    val canLeaveTypes: Boolean get() = kinds.any { it.selected }
    val canFinish: Boolean
        get() = when (selectedScope) {
            null -> false
            ScopeKind.FOLDERS -> folders.any { it.selected }
            else -> true
        } && (draft == null || draft.name.isNotBlank())
}

/** [hint] says why a preset that can't run right now is disabled. */
@Immutable
data class PresetUi(
    val id: Long,
    val title: UiText,
    val body: UiText,
    val enabled: Boolean,
    val hint: UiText?,
    val editable: Boolean
)

@Immutable
data class KindOptionUi(val kind: MediaKind, val label: UiText, val selected: Boolean)

@Immutable
data class ScopeOptionUi(val kind: ScopeKind, val title: UiText, val body: UiText)

@Immutable
data class FolderOptionUi(val uri: String, val name: String, val selected: Boolean)

@Immutable
data class PresetDraftUi(val id: Long, val name: String)

@Immutable
data class ScanResultUi(
    val title: UiText,
    val body: UiText,
    val isError: Boolean,
    val hasResults: Boolean
)

/** The live numbers; [fraction] is null while the amount of work is unknown; [aiStage] offers Skip AI. */
@Immutable
data class ScanProgressUi(
    val stage: UiText,
    val count: UiText?,
    val rate: UiText?,
    val fraction: Float?,
    val aiStage: Boolean = false
)

sealed interface ScanIntent : UiIntent {
    data object Refresh : ScanIntent
    data class PresetSelected(val id: Long?) : ScanIntent
    data object NextClicked : ScanIntent
    data object BackClicked : ScanIntent
    data class KindToggled(val kind: MediaKind) : ScanIntent
    data class ScopeSelected(val kind: ScopeKind) : ScanIntent
    data class FolderToggled(val uri: String) : ScanIntent
    data object AddFolderClicked : ScanIntent
    data class FolderPicked(val treeUri: String?) : ScanIntent
    data object NewPresetClicked : ScanIntent
    data class EditPresetClicked(val id: Long) : ScanIntent
    data class DraftNameChanged(val name: String) : ScanIntent
    data object SavePresetClicked : ScanIntent
    data class DeletePresetClicked(val id: Long) : ScanIntent
    data object DeleteConfirmed : ScanIntent
    data object DeleteDismissed : ScanIntent
    data class NearToggled(val enabled: Boolean) : ScanIntent
    data class AiToggled(val enabled: Boolean) : ScanIntent
    data object StartClicked : ScanIntent
    data object CancelClicked : ScanIntent
    data object SkipAiClicked : ScanIntent
    data object ScanAgainClicked : ScanIntent
    data object ViewResultsClicked : ScanIntent
}

sealed interface ScanEffect : UiEffect {
    data object ViewResults : ScanEffect
    data object PickFolder : ScanEffect
    data class ShowMessage(val message: UiText) : ScanEffect
}
