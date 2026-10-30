package com.vipuljha.dupedigger.presentation.quarantine

import androidx.compose.runtime.Immutable
import com.vipuljha.dupedigger.core.mvi.UiEffect
import com.vipuljha.dupedigger.core.mvi.UiIntent
import com.vipuljha.dupedigger.core.mvi.UiState
import com.vipuljha.dupedigger.core.ui.ContentState
import com.vipuljha.dupedigger.core.ui.UiText
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/** [busy] holds the files being restored or deleted; [confirmDelete] is the file awaiting "Delete permanently". */
@Immutable
data class QuarantineState(
    val content: ContentState<ImmutableList<QuarantineItemUi>> = ContentState.Loading,
    val busy: ImmutableSet<Long> = persistentSetOf(),
    val confirmDelete: QuarantineItemUi? = null,
) : UiState

@Immutable
data class QuarantineItemUi(val id: Long, val name: String, val folder: UiText, val details: UiText)

sealed interface QuarantineIntent : UiIntent {
    data class RestoreClicked(val id: Long) : QuarantineIntent
    data class DeleteClicked(val item: QuarantineItemUi) : QuarantineIntent
    data object DeleteConfirmed : QuarantineIntent
    data object DeleteDismissed : QuarantineIntent
}

sealed interface QuarantineEffect : UiEffect {
    data class ShowMessage(val message: UiText) : QuarantineEffect
}
