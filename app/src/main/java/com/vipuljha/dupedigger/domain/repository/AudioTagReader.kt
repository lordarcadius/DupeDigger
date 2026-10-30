package com.vipuljha.dupedigger.domain.repository

import com.vipuljha.dupedigger.domain.model.AudioTags

/** Duration and tags of an audio file; null when it can't be read or has no duration. */
interface AudioTagReader {
    suspend fun read(sourceUri: String): AudioTags?
}
