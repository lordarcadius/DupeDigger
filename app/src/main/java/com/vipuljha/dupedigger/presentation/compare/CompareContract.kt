package com.vipuljha.dupedigger.presentation.compare

import androidx.compose.runtime.Immutable
import com.vipuljha.dupedigger.core.mvi.UiEffect
import com.vipuljha.dupedigger.core.mvi.UiIntent
import com.vipuljha.dupedigger.core.mvi.UiState
import com.vipuljha.dupedigger.core.ui.ContentState
import com.vipuljha.dupedigger.core.ui.UiText

/** A loaded null means one of the two files left the group, as after a rescan or a delete. */
@Immutable
data class CompareState(val content: ContentState<CompareUi?> = ContentState.Loading) : UiState

/** The keeper on the left, the other file on the right, and the group's similarity when it isn't exact. */
@Immutable
data class CompareUi(val left: ComparePaneUi, val right: ComparePaneUi, val similarity: UiText?)

@Immutable
data class ComparePaneUi(val name: String, val uri: String, val details: UiText)

sealed interface CompareIntent : UiIntent

sealed interface CompareEffect : UiEffect
