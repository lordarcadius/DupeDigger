package com.vipuljha.dupedigger.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import com.vipuljha.dupedigger.core.common.IoDispatcher
import com.vipuljha.dupedigger.domain.repository.EmbeddingInputReader
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Prepares files for the embedding model (D-006).
 * - Images: decoded straight to a long side of [IMAGE_SIDE] px (EXIF orientation applied) and re-encoded as JPEG.
 * - Audio: [CLIP_SECONDS] from a quarter of the way in (skipping intros), mixed to mono and resampled to 16 kHz PCM16 WAV.
 */
@Singleton
class AndroidEmbeddingInputReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : EmbeddingInputReader {

    override suspend fun image(sourceUri: String): ByteArray? = read("image") {
        val source = if (sourceUri.startsWith("file:")) {
            ImageDecoder.createSource(File(URI(sourceUri)))
        } else {
            ImageDecoder.createSource(context.contentResolver, Uri.parse(sourceUri))
        }
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val scale = IMAGE_SIDE.toFloat() / max(info.size.width, info.size.height)
            if (scale < 1f) decoder.setTargetSize((info.size.width * scale).roundToInt().coerceAtLeast(1), (info.size.height * scale).roundToInt().coerceAtLeast(1))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        try {
            ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }.toByteArray()
        } finally {
            bitmap.recycle()
        }
    }

    override suspend fun audio(sourceUri: String): ByteArray? = read("audio") {
        val mono = decodeClip(sourceUri) ?: return@read null
        wav(resample(mono.first, mono.second, TARGET_RATE))
    }

    private suspend fun <T> read(what: String, block: suspend () -> T?): T? = withContext(ioDispatcher) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Timber.w(e, "Couldn't prepare %s for embedding", what)
            null
        } catch (e: RuntimeException) {
            // Decoders report unreadable or unsupported files as plain runtime exceptions.
            Timber.w(e, "Couldn't prepare %s for embedding", what)
            null
        }
    }

    /** Mono float samples of the clip and their sample rate, or null when the file has no audio track. */
    private suspend fun decodeClip(sourceUri: String): Pair<FloatArray, Int>? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            if (sourceUri.startsWith("file:")) extractor.setDataSource(File(URI(sourceUri)).path) else extractor.setDataSource(context, Uri.parse(sourceUri), null)
            val track = (0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true }
                ?: return null
            val format = extractor.getTrackFormat(track)
            extractor.selectTrack(track)
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
            extractor.seekTo((durationUs / 4).coerceAtMost(max(0L, durationUs - CLIP_SECONDS * 1_000_000L)), MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!).also { codec = it }
            decoder.configure(format, null, null, 0)
            decoder.start()
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val samples = ArrayList<Float>()
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            while (samples.size < rate * CLIP_SECONDS) {
                currentCoroutineContext().ensureActive()
                if (!inputDone) {
                    val index = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (index >= 0) {
                        val size = extractor.readSampleData(decoder.getInputBuffer(index)!!, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(index, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val out = decoder.dequeueOutputBuffer(info, TIMEOUT_US)
                if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    rate = decoder.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = decoder.outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else if (out >= 0) {
                    val pcm = decoder.getOutputBuffer(out)!!.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                    val frames = info.size / 2 / channels
                    for (f in 0 until frames) {
                        var sum = 0f
                        for (c in 0 until channels) sum += pcm.get(f * channels + c)
                        samples += sum / channels / Short.MAX_VALUE
                    }
                    decoder.releaseOutputBuffer(out, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }
            return if (samples.isEmpty()) null else samples.toFloatArray() to rate
        } finally {
            codec?.let {
                runCatching { it.stop() }
                it.release()
            }
            extractor.release()
        }
    }

    private fun resample(samples: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to) return samples
        val out = FloatArray((samples.size.toLong() * to / from).toInt())
        for (i in out.indices) {
            val pos = i.toDouble() * from / to
            val j = pos.toInt().coerceAtMost(samples.lastIndex)
            val frac = (pos - j).toFloat()
            out[i] = samples[j] * (1 - frac) + samples[(j + 1).coerceAtMost(samples.lastIndex)] * frac
        }
        return out
    }

    private fun wav(samples: FloatArray): ByteArray {
        val data = samples.size * 2
        val buffer = ByteBuffer.allocate(WAV_HEADER_BYTES + data).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(TARGET_RATE).putInt(TARGET_RATE * 2).putShort(2).putShort(16)
        buffer.put("data".toByteArray()).putInt(data)
        for (s in samples) buffer.putShort((s.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort())
        return buffer.array()
    }

    private companion object {
        const val IMAGE_SIDE = 512
        const val JPEG_QUALITY = 85
        const val CLIP_SECONDS = 10
        const val TARGET_RATE = 16_000
        const val TIMEOUT_US = 10_000L
        const val WAV_HEADER_BYTES = 44
    }
}
