package com.vipuljha.dupedigger.domain.repository

import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.domain.model.EmbeddingBackend
import com.vipuljha.dupedigger.domain.model.EmbeddingInput
import com.vipuljha.dupedigger.domain.model.ModelVariant

/** Turns inputs into L2-normalised vectors with a downloaded model; only one batch runs at a time. */
interface Embedder {
    /**
     * One vector of [dim] floats per input, in order.
     * Fails with ModelUnavailable when the model isn't downloaded, or Unknown when the engine can't start or run.
     */
    suspend fun embed(variant: ModelVariant, inputs: List<EmbeddingInput>, dim: Int, backend: EmbeddingBackend): Outcome<List<FloatArray>>

    /** Releases the loaded model now; the next call loads it again. */
    suspend fun close()
}
