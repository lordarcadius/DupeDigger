package com.vipuljha.dupedigger.presentation.compare

import androidx.lifecycle.viewModelScope
import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.mvi.MviViewModel
import com.vipuljha.dupedigger.core.ui.ContentState
import com.vipuljha.dupedigger.core.ui.toUiText
import com.vipuljha.dupedigger.domain.model.DuplicateGroupItem
import com.vipuljha.dupedigger.domain.usecase.ObserveGroupUseCase
import com.vipuljha.dupedigger.presentation.common.FileRemoval
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Two images of one group side by side, following the group so a rescan or delete shows that a file has gone.
 * The right-hand copy can be deleted right here, with Undo.
 */
@HiltViewModel(assistedFactory = CompareViewModel.Factory::class)
class CompareViewModel @AssistedInject constructor(
    @Assisted fingerprint: String,
    @Assisted("left") leftId: Long,
    @Assisted("right") private val rightId: Long,
    observeGroup: ObserveGroupUseCase,
    private val removal: FileRemoval,
) : MviViewModel<CompareState, CompareIntent, CompareEffect>(CompareState()) {

    private var group: DuplicateGroupItem? = null

    init {
        observeGroup(fingerprint)
            .onEach { item ->
                group = item
                setState {
                    copy(
                        content = ContentState.Success(
                            item?.toCompareUi(
                                leftId,
                                rightId
                            )
                        )
                    )
                }
            }
            .catch {
                setState {
                    copy(
                        content = ContentState.Error(
                            DomainError.Unknown.toUiText(),
                            retryable = false
                        )
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    override fun handleIntent(intent: CompareIntent) {
        when (intent) {
            CompareIntent.DeleteClicked -> group?.removable?.firstOrNull { it.fileId == rightId }
                ?.let { copy ->
                    report { removal.delete(mapOf(copy.fileId to copy.size)) }
                }

            is CompareIntent.TrashAnswered -> report { removal.trashAnswered(intent.accepted) }
            is CompareIntent.UndoClicked -> report { removal.undo(intent.undo) }
        }
    }

    private fun report(events: suspend () -> List<FileRemoval.Event>) {
        viewModelScope.launch { events().forEach { sendEffect(CompareEffect.Removal(it)) } }
    }

    @AssistedFactory
    interface Factory {
        fun create(
            fingerprint: String,
            @Assisted("left") leftId: Long,
            @Assisted("right") rightId: Long
        ): CompareViewModel
    }
}
