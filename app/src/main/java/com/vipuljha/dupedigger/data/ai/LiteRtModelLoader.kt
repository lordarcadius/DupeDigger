package com.vipuljha.dupedigger.data.ai

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EmbeddingEngine
import com.google.ai.edge.litertlm.EmbeddingEngineConfig
import com.google.ai.edge.litertlm.EmbeddingOptions
import com.google.ai.edge.litertlm.InputData
import com.vipuljha.dupedigger.domain.model.EmbeddingBackend
import com.vipuljha.dupedigger.domain.model.EmbeddingInput
import com.vipuljha.dupedigger.domain.model.ModelCapability
import com.vipuljha.dupedigger.domain.model.ModelVariant
import com.vipuljha.dupedigger.domain.repository.DeviceSoc
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** A model loaded into memory; [embed] blocks the calling thread. */
interface LoadedModel : AutoCloseable {
    fun embed(input: EmbeddingInput, dim: Int): FloatArray
}

/** Loads a model file on one backend, throwing when that backend can't start it. */
fun interface ModelLoader {
    fun load(path: String, variant: ModelVariant, backend: EmbeddingBackend): LoadedModel
}

/**
 * LiteRT-LM per D-006: image and audio encoders run on the same backend as text and only for variants that have them,
 * audio needs a 512-token input limit, and images use 70 vision tokens.
 *
 * The NPU backend reads its vendor dispatch library out of the app's native library directory, where Play's
 * conditional delivery puts the one that matches this chip (D-035).
 */
class LiteRtModelLoader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val soc: DeviceSoc,
) : ModelLoader {

    override fun load(path: String, variant: ModelVariant, backend: EmbeddingBackend): LoadedModel {
        val engineBackend = {
            when (backend) {
                EmbeddingBackend.NPU -> Backend.NPU(soc.nativeLibraryDir())
                EmbeddingBackend.GPU -> Backend.GPU()
                EmbeddingBackend.CPU -> Backend.CPU()
            }
        }
        val audio = ModelCapability.AUDIO in variant.capabilities
        val engine = EmbeddingEngine(
            EmbeddingEngineConfig(
                modelPath = path,
                backend = engineBackend(),
                visionBackend = if (ModelCapability.VISION in variant.capabilities) engineBackend() else null,
                audioBackend = if (audio) engineBackend() else null,
                cacheDir = context.cacheDir.path,
                maxInputLength = if (audio) AUDIO_MAX_INPUT_TOKENS else null,
            ),
        )
        try {
            engine.initialize()
        } catch (e: Exception) {
            if (engine.isInitialized()) engine.close()
            throw e
        }
        return object : LoadedModel {
            override fun embed(input: EmbeddingInput, dim: Int): FloatArray =
                engine.computeEmbedding(
                    input.toInputData(),
                    EmbeddingOptions(
                        normalize = true,
                        outputSize = dim,
                        visionTokensPerImage = VISION_TOKENS
                    )
                ).embedding

            override fun close() {
                if (engine.isInitialized()) engine.close()
            }
        }
    }

    /** One vector per call, so a video's keyframes go in together rather than one at a time. */
    private fun EmbeddingInput.toInputData(): List<InputData> = when (this) {
        is EmbeddingInput.Text -> listOf(InputData.Text(text))
        is EmbeddingInput.Image -> listOf(InputData.Image(encoded))
        is EmbeddingInput.Audio -> listOf(InputData.Audio(wav))
        is EmbeddingInput.Video -> frames.map { InputData.Image(it) }
    }

    private companion object {
        const val AUDIO_MAX_INPUT_TOKENS = 512
        const val VISION_TOKENS = 70
    }
}
