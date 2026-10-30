package com.vipuljha.dupedigger.domain.repository

import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.domain.model.ContentHashes

/** Content hashes per D-005; a file whose length no longer matches [size] fails instead of hashing stale bytes. */
interface ContentHasher {
    suspend fun partialHash(sourceUri: String, size: Long): Outcome<ContentHashes>

    suspend fun fullHash(sourceUri: String, size: Long): Outcome<String>
}
