package com.vipuljha.dupedigger.presentation.home

import androidx.compose.runtime.Immutable
import com.vipuljha.dupedigger.core.mvi.UiEffect
import com.vipuljha.dupedigger.core.mvi.UiIntent
import com.vipuljha.dupedigger.core.mvi.UiState
import com.vipuljha.dupedigger.core.ui.ContentState
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.domain.model.MediaKind
import kotlinx.collections.immutable.ImmutableList

@Immutable
data class HomeState(
    val content: ContentState<HomeUiModel> = ContentState.Loading,
) : UiState

/** Empty means no scan has run yet and none is running. */
@Immutable
data class HomeUiModel(
    val reclaimTotal: UiText,
    val reclaimDetail: UiText?,
    val kinds: ImmutableList<KindRowUi>,
    val lastScan: LastScanUi?,
    val scanning: Boolean,
) {
    val isEmpty: Boolean get() = lastScan == null && !scanning
}

@Immutable
data class KindRowUi(val kind: MediaKind, val label: UiText, val size: UiText, val detail: UiText)

@Immutable
data class LastScanUi(val time: UiText, val scope: UiText?, val status: UiText)

sealed interface HomeIntent : UiIntent {
    data object Retry : HomeIntent
    data object OpenScanClicked : HomeIntent
}

sealed interface HomeEffect : UiEffect {
    data object OpenScan : HomeEffect
}
