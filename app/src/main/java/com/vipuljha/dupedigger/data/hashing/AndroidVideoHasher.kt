package com.vipuljha.dupedigger.data.hashing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.vipuljha.dupedigger.core.common.IoDispatcher
import com.vipuljha.dupedigger.domain.model.VIDEO_FRAMES
import com.vipuljha.dupedigger.domain.model.VideoSignature
import com.vipuljha.dupedigger.domain.repository.VideoHasher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Samples [VIDEO_FRAMES] frames at the middle of equal slices of the video, skipping the very start and end, and
 * pHashes each. `OPTION_CLOSEST` takes the exact frame rather than the nearest keyframe, so a re-encode with other
 * keyframe spacing samples the same picture; it costs a decode from the previous keyframe per sample.
 */
@Singleton
class AndroidVideoHasher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : VideoHasher {

    override suspend fun signature(sourceUri: String): VideoSignature? = withContext(ioDispatcher) {
        try {
            MediaMetadataRetriever().use { retriever ->
                if (sourceUri.startsWith("file:")) {
                    retriever.setDataSource(File(URI(sourceUri)).path)
                } else {
                    retriever.setDataSource(context, Uri.parse(sourceUri))
                }
                val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                    ?.takeIf { it > 0 } ?: return@use null
                val frames = (0 until VIDEO_FRAMES).map { i ->
                    ensureActive()
                    val timeUs = durationMs * 1000 * (2 * i + 1) / (2 * VIDEO_FRAMES)
                    val frame = retriever.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST, FRAME_SIZE, FRAME_SIZE)
                        ?: return@use null
                    try {
                        pHashOf(frame)
                    } finally {
                        frame.recycle()
                    }
                }
                VideoSignature(durationMs, frames.toImmutableList())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Timber.w(e, "Couldn't read a video for hashing")
            null
        } catch (e: RuntimeException) {
            // MediaMetadataRetriever reports unreadable or unsupported files as plain RuntimeExceptions.
            Timber.w(e, "Couldn't read a video for hashing")
            null
        }
    }

    private fun pHashOf(frame: Bitmap): Long {
        val (width, height) = frame.width to frame.height
        val pixels = IntArray(width * height).also { frame.getPixels(it, 0, width, 0, 0, width, height) }
        val luma = FloatArray(pixels.size) { i ->
            val p = pixels[i]
            0.299f * Color.red(p) + 0.587f * Color.green(p) + 0.114f * Color.blue(p)
        }
        return perceptualHashes(luma, width, height).pHash
    }

    private companion object {
        const val FRAME_SIZE = 128
    }
}
