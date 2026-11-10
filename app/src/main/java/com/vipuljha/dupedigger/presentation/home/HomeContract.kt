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
    val storage: StorageUi? = null,
) {
    val isEmpty: Boolean get() = lastScan == null && !scanning
}

/**
 * What the device's storage looks like and what clearing the duplicates would change.
 * [usedFraction] and [duplicateFraction] are shares of the whole volume, for the meter; null when no volume could be read.
 */
@Immutable
data class StorageUi(
    val headline: UiText,
    val freeNow: UiText,
    val usedFraction: Float,
    val duplicateFraction: Float,
    val afterCleanup: UiText?,
)

@Immutable
data class KindRowUi(val kind: MediaKind, val label: UiText, val size: UiText, val detail: UiText)

@Immutable
/** [stats] is how long the scan took and how many files it found, as far as the record knows them. */
data class LastScanUi(val time: UiText, val scope: UiText?, val status: UiText, val stats: UiText? = null)

sealed interface HomeIntent : UiIntent {
    data object Retry : HomeIntent
    data object OpenScanClicked : HomeIntent
}

sealed interface HomeEffect : UiEffect {
    data object OpenScan : HomeEffect
}
