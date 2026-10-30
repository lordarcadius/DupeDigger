package com.vipuljha.dupedigger.domain.repository

/** Prepares files for the embedding model; null when the file can't be read or decoded. */
interface EmbeddingInputReader {
    /** The image re-encoded small (JPEG, long side about 512 px), not the original file. */
    suspend fun image(sourceUri: String): ByteArray?

    /** A clip of the audio as 16 kHz mono PCM16 WAV. */
    suspend fun audio(sourceUri: String): ByteArray?
}
