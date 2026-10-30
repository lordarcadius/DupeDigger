package com.vipuljha.dupedigger.domain.model

/** What the embedding model reads: text, an encoded image (JPEG/PNG bytes), or 16 kHz mono PCM16 WAV bytes. */
sealed interface EmbeddingInput {
    data class Text(val text: String) : EmbeddingInput
    class Image(val encoded: ByteArray) : EmbeddingInput
    class Audio(val wav: ByteArray) : EmbeddingInput
}

/** CPU is the default (D-006); GPU is a preference that falls back to CPU when it can't start. */
enum class EmbeddingBackend { CPU, GPU }

/** Vector size used for similarity: near-lossless Matryoshka truncation per D-006. */
const val EMBEDDING_DIM = 256

/** Stored vectors are keyed by model and size, since vectors from another model or size can't be compared. */
fun embeddingKey(variant: ModelVariant, dim: Int = EMBEDDING_DIM): String = "${variant.id}@$dim"

/** EmbeddingGemma's symmetric-similarity prompt (D-006); document text is embedded after it. */
const val SIMILARITY_PROMPT = "task: sentence similarity | query: "

/** A file the semantic stage may embed, with its stored vector for the active model when there is one. */
class EmbeddedFile(
    val id: Long,
    val sourceUri: String,
    val name: String,
    val mimeType: String?,
    val kind: MediaKind,
    val size: Long,
    val vector: FloatArray?,
)
