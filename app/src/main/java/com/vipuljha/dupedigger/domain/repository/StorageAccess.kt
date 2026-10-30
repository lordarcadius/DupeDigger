package com.vipuljha.dupedigger.domain.repository

import com.vipuljha.dupedigger.domain.model.StorageAccessState

interface StorageAccess {
    /** Reads the current grants; cheap enough to call on every resume. */
    fun current(): StorageAccessState

    /** The runtime permissions to request for media access on this API level. */
    val mediaPermissions: List<String>
}
