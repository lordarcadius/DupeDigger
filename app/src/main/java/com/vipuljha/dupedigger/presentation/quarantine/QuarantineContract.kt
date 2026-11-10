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

/**
 * Files are picked with a checkbox and acted on together, the way Results works.
 * [selection] holds the picked ids, pruned to what is still here; [working] covers a restore or delete in flight;
 * [confirmDelete] is how many files "Delete" would erase for good.
 */
@Immutable
data class QuarantineState(
    val content: ContentState<ImmutableList<QuarantineItemUi>> = ContentState.Loading,
    val selection: ImmutableSet<Long> = persistentSetOf(),
    val working: Boolean = false,
    val confirmDelete: Int? = null,
) : UiState {
    val hasSelection: Boolean get() = selection.isNotEmpty()
}

@Immutable
data class QuarantineItemUi(
    val id: Long,
    val name: String,
    val folder: UiText,
    val details: UiText,
    val selected: Boolean = false,
)

sealed interface QuarantineIntent : UiIntent {
    data class ItemToggled(val id: Long) : QuarantineIntent
    data object SelectAllToggled : QuarantineIntent
    data object RestoreClicked : QuarantineIntent
    data object DeleteClicked : QuarantineIntent
    data object DeleteConfirmed : QuarantineIntent
    data object DeleteDismissed : QuarantineIntent
}

sealed interface QuarantineEffect : UiEffect {
    data class ShowMessage(val message: UiText) : QuarantineEffect
}
