package com.vipuljha.dupedigger.presentation.group

import androidx.lifecycle.viewModelScope
import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.mvi.MviViewModel
import com.vipuljha.dupedigger.core.ui.ContentState
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.core.ui.toUiText
import com.vipuljha.dupedigger.domain.model.DuplicateGroupItem
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.usecase.ChooseKeeperUseCase
import com.vipuljha.dupedigger.domain.usecase.GetViewTargetUseCase
import com.vipuljha.dupedigger.domain.usecase.MarkNotDuplicatesUseCase
import com.vipuljha.dupedigger.domain.usecase.ObserveGroupUseCase
import com.vipuljha.dupedigger.domain.usecase.ReadMediaInfoUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * One group by its content fingerprint, so it survives rescans that renumber groups.
 * - Keep choices and "not duplicates" are saved by fingerprint; the list here follows the database.
 * - Image and video dimensions are read once per file, in the background.
 */
@HiltViewModel(assistedFactory = GroupViewModel.Factory::class)
class GroupViewModel @AssistedInject constructor(
    @Assisted private val fingerprint: String,
    observeGroup: ObserveGroupUseCase,
    private val chooseKeeper: ChooseKeeperUseCase,
    private val markNotDuplicates: MarkNotDuplicatesUseCase,
    private val readMediaInfo: ReadMediaInfoUseCase,
    private val getViewTarget: GetViewTargetUseCase,
) : MviViewModel<GroupState, GroupIntent, GroupEffect>(GroupState()) {

    private var group: DuplicateGroupItem? = null
    private val requested = mutableSetOf<Long>()

    init {
        observeGroup(fingerprint)
            .onEach { item ->
                group = item
                setState { copy(content = ContentState.Success(item?.toDetailUi())) }
                item?.let(::readDimensions)
            }
            .catch { setState { copy(content = ContentState.Error(DomainError.Unknown.toUiText(), retryable = false)) } }
            .launchIn(viewModelScope)
    }

    override fun handleIntent(intent: GroupIntent) {
        val current = group ?: return
        val members = listOf(current.keeper) + current.removable
        when (intent) {
            is GroupIntent.KeepClicked -> members.firstOrNull { it.fileId == intent.fileId }?.let {
                viewModelScope.launch { chooseKeeper(fingerprint, it) }
            }
            is GroupIntent.OpenClicked -> members.firstOrNull { it.fileId == intent.fileId }?.let { member ->
                viewModelScope.launch {
                    val target = getViewTarget(member)
                    sendEffect(
                        if (target != null) GroupEffect.OpenFile(target.uri, target.mimeType)
                        else GroupEffect.ShowMessage(UiText.res(R.string.group_open_failed)),
                    )
                }
            }
            GroupIntent.NotDuplicatesClicked -> viewModelScope.launch {
                markNotDuplicates(fingerprint)
                sendEffect(GroupEffect.Close)
            }
        }
    }

    private fun readDimensions(item: DuplicateGroupItem) {
        if (item.kind != MediaKind.IMAGE && item.kind != MediaKind.VIDEO) return
        (listOf(item.keeper) + item.removable).filter { requested.add(it.fileId) }.forEach { member ->
            viewModelScope.launch {
                val info = readMediaInfo(member, item.kind) ?: return@launch
                val (w, h) = info.width to info.height
                if (w != null && h != null) {
                    setState { copy(resolutions = (resolutions + (member.fileId to UiText.res(R.string.group_resolution, w, h))).toImmutableMap()) }
                }
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(fingerprint: String): GroupViewModel
    }
}
