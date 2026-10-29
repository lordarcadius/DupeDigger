package com.vipuljha.dupedigger.core.common

sealed interface DomainError {
    data object PermissionDenied : DomainError
    data object NotFound : DomainError
    data object Io : DomainError
    data object ModelUnavailable : DomainError
    data object Unknown : DomainError
}
