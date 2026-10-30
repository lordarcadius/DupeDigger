package com.vipuljha.dupedigger.core.ui

import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.common.Outcome

sealed interface ContentState<out T> {
    data object Loading : ContentState<Nothing>
    data class Success<T>(val data: T) : ContentState<T>
    data class Error(
        val message: UiText,
        val retryable: Boolean = true,
        val retrying: Boolean = false,
    ) : ContentState<Nothing>
}

fun <T> ContentState<T>.reloading(): ContentState<T> = when (this) {
    ContentState.Loading -> this
    is ContentState.Success -> this
    is ContentState.Error -> if (retrying) this else copy(retrying = true)
}

fun <T> Outcome<T>.toContentState(): ContentState<T> = when (this) {
    is Outcome.Success -> ContentState.Success(data)
    is Outcome.Failure -> ContentState.Error(error.toUiText())
}

fun DomainError.toUiText(): UiText = UiText.Resource(
    when (this) {
        DomainError.PermissionDenied -> R.string.error_permission_denied
        DomainError.NotFound -> R.string.error_not_found
        DomainError.Io -> R.string.error_io
        DomainError.ModelUnavailable -> R.string.error_model_unavailable
        DomainError.WouldRemoveAllCopies -> R.string.error_would_remove_all_copies
        DomainError.Unknown -> R.string.error_unknown
    },
)
