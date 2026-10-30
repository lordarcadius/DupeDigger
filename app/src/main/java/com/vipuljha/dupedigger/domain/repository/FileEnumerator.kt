package com.vipuljha.dupedigger.domain.repository

import com.vipuljha.dupedigger.domain.model.DiscoveredFile
import com.vipuljha.dupedigger.domain.model.ScanFilter
import com.vipuljha.dupedigger.domain.model.ScanScope
import kotlinx.coroutines.flow.Flow

interface FileEnumerator {
    /** A cold, cancellable stream of every file in [scope] that [filter] keeps; files are never opened. */
    fun enumerate(scope: ScanScope, filter: ScanFilter): Flow<DiscoveredFile>
}
