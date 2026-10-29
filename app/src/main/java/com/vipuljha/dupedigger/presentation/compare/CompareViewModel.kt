package com.vipuljha.dupedigger.presentation.compare

import androidx.lifecycle.viewModelScope
import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.mvi.MviViewModel
import com.vipuljha.dupedigger.core.ui.ContentState
import com.vipuljha.dupedigger.core.ui.toUiText
import com.vipuljha.dupedigger.domain.usecase.ObserveGroupUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Two images of one group side by side, following the group so a rescan or delete shows that a file has gone. */
@HiltViewModel(assistedFactory = CompareViewModel.Factory::class)
class CompareViewModel @AssistedInject constructor(
    @Assisted fingerprint: String,
    @Assisted("left") leftId: Long,
    @Assisted("right") rightId: Long,
    observeGroup: ObserveGroupUseCase,
) : MviViewModel<CompareState, CompareIntent, CompareEffect>(CompareState()) {

    init {
        observeGroup(fingerprint)
            .onEach { item -> setState { copy(content = ContentState.Success(item?.toCompareUi(leftId, rightId))) } }
            .catch { setState { copy(content = ContentState.Error(DomainError.Unknown.toUiText(), retryable = false)) } }
            .launchIn(viewModelScope)
    }

    override fun handleIntent(intent: CompareIntent) = Unit

    @AssistedFactory
    interface Factory {
        fun create(fingerprint: String, @Assisted("left") leftId: Long, @Assisted("right") rightId: Long): CompareViewModel
    }
}
