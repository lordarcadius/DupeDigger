package com.vipuljha.dupedigger.core.common

sealed interface DomainError {
    data object PermissionDenied : DomainError
    data object NotFound : DomainError
    data object Io : DomainError
    data object ModelUnavailable : DomainError

    /** The request would remove every copy in a duplicate group. */
    data object WouldRemoveAllCopies : DomainError

    /** A built-in scan preset can't be changed or deleted. */
    data object BuiltInPreset : DomainError

    /** A scan preset needs a name and something to scan. */
    data object InvalidPreset : DomainError
    data object Unknown : DomainError
}
