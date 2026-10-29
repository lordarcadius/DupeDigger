package com.vipuljha.dupedigger.domain.model

/** How much media the app may read through MediaStore. PARTIAL is a 34+ "selected photos" grant or a subset of media types. */
enum class MediaAccess { NONE, PARTIAL, FULL }

data class StorageAccessState(
    val media: MediaAccess,
    val allFiles: Boolean,
)
