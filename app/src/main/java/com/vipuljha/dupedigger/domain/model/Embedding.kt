package com.vipuljha.dupedigger.domain.model

/** What the embedding model reads: text, an encoded image (JPEG/PNG bytes), or 16 kHz mono PCM16 WAV bytes. */
sealed interface EmbeddingInput {
    data class Text(val text: String) : EmbeddingInput
    class Image(val encoded: ByteArray) : EmbeddingInput
    class Audio(val wav: ByteArray) : EmbeddingInput
}

/** CPU is the default (D-006); GPU is a preference that falls back to CPU when it can't start. */
enum class EmbeddingBackend { CPU, GPU }
