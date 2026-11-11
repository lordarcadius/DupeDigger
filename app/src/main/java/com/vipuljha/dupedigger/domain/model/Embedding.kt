package com.vipuljha.dupedigger.domain.model

/** What the embedding model reads: text, an encoded image (JPEG/PNG bytes), or 16 kHz mono PCM16 WAV bytes. */
sealed interface EmbeddingInput {
    data class Text(val text: String) : EmbeddingInput
    class Image(val encoded: ByteArray) : EmbeddingInput
    class Audio(val wav: ByteArray) : EmbeddingInput

    /** Keyframes of one video, in time order; the vision encoder reads them together and returns a single vector. */
    class Video(val frames: List<ByteArray>) : EmbeddingInput
}

/**
 * Keyframes sampled per video for its vector. Three spreads over the whole video while staying well inside the
 * model's input window at 70 vision tokens a frame, and costs three decodes rather than one a second.
 */
const val VIDEO_EMBED_FRAMES = 3

/**
 * CPU is the default (D-006). Each of the others is a preference, not a promise: the engine falls back down the
 * list — NPU to GPU to CPU — when a backend can't start or the model has no build for it.
 */
enum class EmbeddingBackend { CPU, GPU, NPU }

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
    /** False for a file an earlier scan indexed that this one's scope doesn't cover. */
    val seenNow: Boolean = true,
)
