package com.vipuljha.dupedigger.presentation.quarantine

import androidx.lifecycle.viewModelScope
import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.core.mvi.MviViewModel
import com.vipuljha.dupedigger.core.ui.ContentState
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.core.ui.toUiText
import com.vipuljha.dupedigger.domain.repository.QuarantineRepository
import com.vipuljha.dupedigger.domain.usecase.DeleteQuarantinedUseCase
import com.vipuljha.dupedigger.domain.usecase.RestoreQuarantinedUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Files moved out of the way by a delete: put each back, or delete it for good after a confirmation. */
@HiltViewModel
class QuarantineViewModel @Inject constructor(
    quarantine: QuarantineRepository,
    private val restore: RestoreQuarantinedUseCase,
    private val deletePermanently: DeleteQuarantinedUseCase,
) : MviViewModel<QuarantineState, QuarantineIntent, QuarantineEffect>(QuarantineState()) {

    init {
        quarantine.observeAll()
            .onEach { files ->
                val now = System.currentTimeMillis()
                setState { copy(content = ContentState.Success(files.map { it.toUi(now) }.toImmutableList())) }
            }
            .catch { setState { copy(content = ContentState.Error(DomainError.Unknown.toUiText(), retryable = false)) } }
            .launchIn(viewModelScope)
    }

    override fun handleIntent(intent: QuarantineIntent) {
        when (intent) {
            is QuarantineIntent.RestoreClicked -> work(intent.id) {
                val failed = restore(listOf(intent.id))
                sendEffect(QuarantineEffect.ShowMessage(restoreMessage(restored = 1 - failed, failed = failed)))
            }
            is QuarantineIntent.DeleteClicked -> setState { copy(confirmDelete = intent.item) }
            QuarantineIntent.DeleteDismissed -> setState { copy(confirmDelete = null) }
            QuarantineIntent.DeleteConfirmed -> {
                val item = currentState.confirmDelete ?: return
                setState { copy(confirmDelete = null) }
                work(item.id) {
                    val outcome = deletePermanently(item.id)
                    sendEffect(
                        QuarantineEffect.ShowMessage(
                            if (outcome is Outcome.Failure) outcome.error.toUiText() else UiText.res(R.string.quarantine_deleted, item.name),
                        ),
                    )
                }
            }
        }
    }

    /** Runs [block] for one file at a time; a second tap while it runs is ignored. */
    private fun work(id: Long, block: suspend () -> Unit) {
        if (id in currentState.busy) return
        setState { copy(busy = (busy + id).toImmutableSet()) }
        viewModelScope.launch {
            block()
            setState { copy(busy = (busy - id).toImmutableSet()) }
        }
    }
}
