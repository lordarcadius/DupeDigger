package com.vipuljha.dupedigger.presentation.group

import androidx.compose.runtime.Immutable
import com.vipuljha.dupedigger.core.mvi.UiEffect
import com.vipuljha.dupedigger.core.mvi.UiIntent
import com.vipuljha.dupedigger.core.mvi.UiState
import com.vipuljha.dupedigger.core.ui.ContentState
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.domain.model.MediaKind
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf

/** A loaded null group means it is gone: a newer scan or a delete changed it. [resolutions] fill in as files are read. */
@Immutable
data class GroupState(
    val content: ContentState<GroupDetailUi?> = ContentState.Loading,
    val resolutions: ImmutableMap<Long, UiText> = persistentMapOf(),
) : UiState

@Immutable
data class GroupDetailUi(val title: String, val summary: UiText, val members: ImmutableList<MemberUi>)

/** [thumbnail] is the file's own URI for images and videos, null for kinds without one. */
@Immutable
data class MemberUi(
    val fileId: Long,
    val kind: MediaKind,
    val name: String,
    val location: UiText,
    val details: UiText,
    val isKeeper: Boolean,
    val thumbnail: String?,
)

sealed interface GroupIntent : UiIntent {
    data class KeepClicked(val fileId: Long) : GroupIntent
    data class OpenClicked(val fileId: Long) : GroupIntent
    data object NotDuplicatesClicked : GroupIntent
}

sealed interface GroupEffect : UiEffect {
    data class OpenFile(val uri: String, val mimeType: String?) : GroupEffect
    data class ShowMessage(val message: UiText) : GroupEffect
    data object Close : GroupEffect
}
