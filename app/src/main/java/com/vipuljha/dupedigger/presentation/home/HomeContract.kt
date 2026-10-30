package com.vipuljha.dupedigger.presentation.home

import androidx.compose.runtime.Immutable
import com.vipuljha.dupedigger.core.mvi.UiEffect
import com.vipuljha.dupedigger.core.mvi.UiIntent
import com.vipuljha.dupedigger.core.mvi.UiState
import com.vipuljha.dupedigger.core.ui.ContentState
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.domain.model.MediaKind
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

enum class QuickAction { Everything, Media, Folder }

@Immutable
data class HomeState(
    val content: ContentState<HomeUiModel> = ContentState.Loading,
    val quickActions: ImmutableList<QuickActionUi> = persistentListOf(),
    val startingAction: QuickAction? = null,
) : UiState

/** [hint] says why a disabled action can't run. */
@Immutable
data class QuickActionUi(val action: QuickAction, val enabled: Boolean, val hint: UiText?)

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
    data object Refresh : HomeIntent
    data object Retry : HomeIntent
    data object OpenScanClicked : HomeIntent
    data class QuickActionClicked(val action: QuickAction) : HomeIntent
    data class FolderPicked(val treeUri: String?) : HomeIntent
}

sealed interface HomeEffect : UiEffect {
    data object OpenScan : HomeEffect
    data object PickFolder : HomeEffect
    data class ShowMessage(val message: UiText) : HomeEffect
}
