package com.vipuljha.dupedigger.data.ai

import android.content.ComponentCallbacks2
import com.vipuljha.dupedigger.core.common.ApplicationScope
import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.common.IoDispatcher
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.domain.model.EmbeddingBackend
import com.vipuljha.dupedigger.domain.model.EmbeddingInput
import com.vipuljha.dupedigger.domain.model.ModelVariant
import com.vipuljha.dupedigger.domain.repository.Embedder
import com.vipuljha.dupedigger.domain.repository.ModelDownloads
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's one embedding engine.
 * - Loads lazily on the IO dispatcher on first use, keeping one model and backend loaded; asking for another reloads.
 * - A GPU that can't start the model falls back to CPU, and the fallback is kept until the model or backend changes.
 * - One batch runs at a time; the engine closes [IDLE_CLOSE_MS] after the last batch, or when the app is trimmed in the
 *   background ([onTrimMemory]), and loads again on the next call.
 */
@Singleton
class LiteRtEmbedder @Inject constructor(
    private val downloads: ModelDownloads,
    private val loader: ModelLoader,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    @param:ApplicationScope private val appScope: CoroutineScope,
) : Embedder {

    private val mutex = Mutex()
    private var loaded: LoadedModel? = null
    private var loadedKey: Pair<String, EmbeddingBackend>? = null
    private var idleClose: Job? = null

    override suspend fun embed(
        variant: ModelVariant,
        inputs: List<EmbeddingInput>,
        dim: Int,
        backend: EmbeddingBackend,
    ): Outcome<List<FloatArray>> = mutex.withLock {
        idleClose?.cancel()
        try {
            val model = withContext(ioDispatcher) { modelFor(variant, backend) }
                ?: return@withLock Outcome.Failure(DomainError.ModelUnavailable)
            val vectors = withContext(ioDispatcher) {
                inputs.map {
                    currentCoroutineContext().ensureActive()
                    model.embed(it, dim)
                }
            }
            Outcome.Success(vectors)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Embedding failed")
            closeLoaded()
            Outcome.Failure(DomainError.Unknown, e)
        } finally {
            idleClose = appScope.launch {
                delay(IDLE_CLOSE_MS)
                close()
            }
        }
    }

    override suspend fun close() = mutex.withLock { closeLoaded() }

    /** Called from the Application; frees the model once the app's process is in the background list. */
    fun onTrimMemory(level: Int) {
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) appScope.launch { close() }
    }

    private fun modelFor(variant: ModelVariant, backend: EmbeddingBackend): LoadedModel? {
        val path = downloads.installedPath(variant) ?: return null
        val key = variant.id to backend
        loaded?.takeIf { loadedKey == key }?.let { return it }
        closeLoaded()
        val model = if (backend == EmbeddingBackend.GPU) {
            try {
                loader.load(path, variant, EmbeddingBackend.GPU)
            } catch (e: Exception) {
                Timber.w(e, "GPU couldn't start %s; using CPU", variant.id)
                loader.load(path, variant, EmbeddingBackend.CPU)
            }
        } else {
            loader.load(path, variant, EmbeddingBackend.CPU)
        }
        loaded = model
        loadedKey = key
        return model
    }

    private fun closeLoaded() {
        loaded?.close()
        loaded = null
        loadedKey = null
    }

    companion object {
        const val IDLE_CLOSE_MS = 60_000L
    }
}
