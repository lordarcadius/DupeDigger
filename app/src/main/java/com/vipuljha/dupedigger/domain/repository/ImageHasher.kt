package com.vipuljha.dupedigger.domain.repository

import com.vipuljha.dupedigger.domain.model.ImageHashes

/** Perceptual hashes of an image, decoded small with its EXIF orientation applied; null when it can't be decoded. */
interface ImageHasher {
    suspend fun hash(sourceUri: String): ImageHashes?
}
