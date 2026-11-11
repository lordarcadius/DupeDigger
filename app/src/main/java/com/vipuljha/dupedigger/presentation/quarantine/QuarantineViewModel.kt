package com.vipuljha.dupedigger.presentation.quarantine

import androidx.lifecycle.viewModelScope
import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.mvi.MviViewModel
import com.vipuljha.dupedigger.core.ui.ContentState
import com.vipuljha.dupedigger.core.ui.toUiText
import com.vipuljha.dupedigger.domain.repository.QuarantineRepository
import com.vipuljha.dupedigger.domain.usecase.DeleteQuarantinedUseCase
import com.vipuljha.dupedigger.domain.usecase.RestoreQuarantinedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Files moved out of the way by a delete: pick them with a checkbox, then put them all back or erase them.
 * The selection is pruned to the files still here, so an action never names one that has already gone.
 */
@HiltViewModel
class QuarantineViewModel @Inject constructor(
    quarantine: QuarantineRepository,
    private val restore: RestoreQuarantinedUseCase,
    private val deleteAll: DeleteQuarantinedUseCase,
) : MviViewModel<QuarantineState, QuarantineIntent, QuarantineEffect>(QuarantineState()) {

    init {
        quarantine.observeAll()
            .onEach { files ->
                val now = System.currentTimeMillis()
                val here = files.map { it.id }.toSet()
                setState {
                    val kept = selection.filterTo(mutableSetOf()) { it in here }
                    copy(
                        content = ContentState.Success(files.map { it.toUi(now, it.id in kept) }
                            .toImmutableList()),
                        selection = kept.toImmutableSet(),
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

    override fun handleIntent(intent: QuarantineIntent) {
        when (intent) {
            is QuarantineIntent.ItemToggled -> select(
                if (intent.id in currentState.selection) currentState.selection - intent.id else currentState.selection + intent.id,
            )

            QuarantineIntent.SelectAllToggled ->
                select(if (allSelected()) emptySet() else items().map { it.id }.toSet())

            QuarantineIntent.RestoreClicked -> work { picked ->
                val failed = restore(picked)
                sendEffect(
                    QuarantineEffect.ShowMessage(
                        restoreMessage(
                            restored = picked.size - failed,
                            failed = failed
                        )
                    )
                )
            }

            QuarantineIntent.DeleteClicked -> currentState.selection.size.takeIf { it > 0 }
                ?.let { count ->
                    setState { copy(confirmDelete = count) }
                }

            QuarantineIntent.DeleteDismissed -> setState { copy(confirmDelete = null) }
            QuarantineIntent.DeleteConfirmed -> {
                setState { copy(confirmDelete = null) }
                work { picked ->
                    val failed = deleteAll(picked)
                    sendEffect(
                        QuarantineEffect.ShowMessage(
                            deletedMessage(
                                deleted = picked.size - failed,
                                failed = failed
                            )
                        )
                    )
                }
            }
        }
    }

    private fun items(): List<QuarantineItemUi> =
        (currentState.content as? ContentState.Success)?.data.orEmpty()

    private fun allSelected(): Boolean =
        items().isNotEmpty() && currentState.selection.size >= items().size

    /** Runs [block] over the current selection; a second tap while one runs is ignored. */
    private fun work(block: suspend (picked: List<Long>) -> Unit) {
        val picked = currentState.selection.toList()
        if (currentState.working || picked.isEmpty()) return
        setState { copy(working = true) }
        viewModelScope.launch {
            block(picked)
            setState { copy(working = false) }
        }
    }

    private fun select(ids: Set<Long>) = setState {
        copy(
            selection = if (ids.isEmpty()) persistentSetOf() else ids.toImmutableSet(),
            content = (content as? ContentState.Success)
                ?.let {
                    ContentState.Success(it.data.map { item -> item.copy(selected = item.id in ids) }
                        .toImmutableList())
                }
                ?: content,
        )
    }
}
