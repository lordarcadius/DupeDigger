package com.vipuljha.dupedigger.domain.repository

import com.vipuljha.dupedigger.domain.model.VideoSignature

/** A video's duration and the pHashes of its sampled frames; null when it can't be read or has no duration. */
interface VideoHasher {
    suspend fun signature(sourceUri: String): VideoSignature?
}
