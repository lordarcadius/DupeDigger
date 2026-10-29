package com.vipuljha.dupedigger.presentation.scan

import androidx.compose.runtime.Immutable
import com.vipuljha.dupedigger.core.mvi.UiEffect
import com.vipuljha.dupedigger.core.mvi.UiIntent
import com.vipuljha.dupedigger.core.mvi.UiState
import com.vipuljha.dupedigger.core.ui.UiText
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

@Immutable
data class ScanState(
    val phase: ScanPhase = ScanPhase.Loading,
    val scopes: ImmutableList<ScopeOptionUi> = persistentListOf(),
    val selectedScope: ScopeKind? = null,
    val nearEnabled: Boolean = true,
    val starting: Boolean = false,
    val cancelling: Boolean = false,
) : UiState

@Immutable
data class ScopeOptionUi(val kind: ScopeKind, val title: UiText, val body: UiText)

@Immutable
data class ScanResultUi(val title: UiText, val body: UiText, val isError: Boolean, val hasResults: Boolean)

/** The live numbers; [fraction] is null while the amount of work is unknown. */
@Immutable
data class ScanProgressUi(val stage: UiText, val count: UiText?, val rate: UiText?, val fraction: Float?)

sealed interface ScanIntent : UiIntent {
    data object Refresh : ScanIntent
    data class ScopeSelected(val kind: ScopeKind) : ScanIntent
    data class NearToggled(val enabled: Boolean) : ScanIntent
    data object StartClicked : ScanIntent
    data object CancelClicked : ScanIntent
    data object ScanAgainClicked : ScanIntent
    data object ViewResultsClicked : ScanIntent
}

sealed interface ScanEffect : UiEffect {
    data object ViewResults : ScanEffect
    data class ShowMessage(val message: UiText) : ScanEffect
}
