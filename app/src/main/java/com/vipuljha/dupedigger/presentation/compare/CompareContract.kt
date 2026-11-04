package com.vipuljha.dupedigger.presentation.compare

import androidx.compose.runtime.Immutable
import com.vipuljha.dupedigger.core.mvi.UiEffect
import com.vipuljha.dupedigger.core.mvi.UiIntent
import com.vipuljha.dupedigger.core.mvi.UiState
import com.vipuljha.dupedigger.core.ui.ContentState
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.presentation.common.FileRemoval
import com.vipuljha.dupedigger.presentation.results.UndoUi

/** A loaded null means one of the two files left the group, as after a rescan or a delete. */
@Immutable
data class CompareState(val content: ContentState<CompareUi?> = ContentState.Loading) : UiState

/** The keeper on the left, the other file on the right, and the group's similarity when it isn't exact. */
@Immutable
data class CompareUi(val left: ComparePaneUi, val right: ComparePaneUi, val similarity: UiText?)

@Immutable
data class ComparePaneUi(val name: String, val uri: String, val details: UiText)

/** Delete removes the right-hand file, never the keeper on the left. */
sealed interface CompareIntent : UiIntent {
    data object DeleteClicked : CompareIntent
    data class TrashAnswered(val accepted: Boolean) : CompareIntent
    data class UndoClicked(val undo: UndoUi) : CompareIntent
}

sealed interface CompareEffect : UiEffect {
    data class Removal(val event: FileRemoval.Event) : CompareEffect
}
