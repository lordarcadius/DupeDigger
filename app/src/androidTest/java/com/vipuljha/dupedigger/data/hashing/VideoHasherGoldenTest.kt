package com.vipuljha.dupedigger.data.hashing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vipuljha.dupedigger.domain.detector.durationsMatch
import com.vipuljha.dupedigger.domain.detector.framesMatch
import com.vipuljha.dupedigger.domain.detector.groupNearVideos
import com.vipuljha.dupedigger.domain.detector.hammingDistance
import com.vipuljha.dupedigger.domain.model.VideoSignature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** P5-03: a video re-encoded at another size, bitrate and keyframe spacing matches; another video or length doesn't. */
@RunWith(AndroidJUnit4::class)
class VideoHasherGoldenTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, "golden-video")
    private val hasher = AndroidVideoHasher(context, Dispatchers.IO)
    private val threshold = 10

    @Before
    fun setUp() {
        dir.deleteRecursively()
        dir.mkdirs()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    /**
     * Frame [t] of a moving scene, drawn in proportion to the frame size so every resolution shows the same picture.
     * The [other] scene has its own layout and motion: wide bars sweeping down instead of shapes drifting across.
     */
    private fun drawScene(canvas: Canvas, w: Int, h: Int, t: Int, other: Boolean) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (other) {
            canvas.drawColor(Color.rgb(235, 230, 210))
            paint.color = Color.rgb(20, 20, 30)
            for (i in 0 until 3) {
                val y = h * ((i / 3f + t * 0.01f) % 1f)
                canvas.drawRect(0f, y, w * (0.4f + 0.2f * i), y + h * 0.12f, paint)
            }
            paint.color = Color.rgb(200, 40, 40)
            canvas.drawCircle(w * 0.85f, h * (0.8f - t * 0.01f), h * 0.1f, paint)
            return
        }
        canvas.drawColor(Color.rgb(30, 50, 120))
        val shapes = listOf(Color.rgb(250, 240, 220), Color.rgb(240, 80, 60), Color.rgb(90, 220, 120))
        shapes.forEachIndexed { i, color ->
            paint.color = color
            val x = w * ((0.15f + 0.3f * i + t * 0.012f * (i + 1)) % 1f)
            val y = h * (0.3f + 0.4f * ((t * 0.02f + i * 0.33f) % 1f))
            if (i == 1) canvas.drawRect(x - w * 0.08f, y - h * 0.12f, x + w * 0.08f, y + h * 0.12f, paint) else canvas.drawCircle(x, y, h * 0.15f, paint)
        }
    }

    /** Encodes [frames] frames at 15 fps as H.264 in MP4, filling YUV planes directly and stamping each frame's time. */
    private fun encode(name: String, w: Int, h: Int, bitrate: Int, keyframeSeconds: Int, frames: Int, other: Boolean = false): File {
        val file = File(dir, name)
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, w, h).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, keyframeSeconds)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(w * h)
        val info = MediaCodec.BufferInfo()
        var track = -1
        var queued = 0
        var done = false
        while (!done) {
            if (queued <= frames) {
                val index = codec.dequeueInputBuffer(10_000)
                if (index >= 0) {
                    val timeUs = queued * 1_000_000L / FPS
                    if (queued == frames) {
                        codec.queueInputBuffer(index, 0, 0, timeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                    } else {
                        drawScene(Canvas(bitmap), w, h, queued, other)
                        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
                        val image = codec.getInputImage(index)!!
                        val (yPlane, uPlane, vPlane) = image.planes
                        for (y in 0 until h) for (x in 0 until w) {
                            val p = pixels[y * w + x]
                            val (r, g, b) = Triple(Color.red(p), Color.green(p), Color.blue(p))
                            yPlane.buffer.put(y * yPlane.rowStride + x * yPlane.pixelStride, (0.257 * r + 0.504 * g + 0.098 * b + 16).toInt().toByte())
                            if (x % 2 == 0 && y % 2 == 0) {
                                val (cx, cy) = x / 2 to y / 2
                                uPlane.buffer.put(cy * uPlane.rowStride + cx * uPlane.pixelStride, (-0.148 * r - 0.291 * g + 0.439 * b + 128).toInt().toByte())
                                vPlane.buffer.put(cy * vPlane.rowStride + cx * vPlane.pixelStride, (0.439 * r - 0.368 * g - 0.071 * b + 128).toInt().toByte())
                            }
                        }
                        codec.queueInputBuffer(index, 0, w * h * 3 / 2, timeUs, 0)
                    }
                    queued++
                }
            }
            val out = codec.dequeueOutputBuffer(info, 10_000)
            when {
                out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    track = muxer.addTrack(codec.outputFormat)
                    muxer.start()
                }
                out >= 0 -> {
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) {
                        muxer.writeSampleData(track, codec.getOutputBuffer(out)!!, info)
                    }
                    codec.releaseOutputBuffer(out, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) done = true
                }
            }
        }
        codec.stop()
        codec.release()
        muxer.stop()
        muxer.release()
        return file
    }

    private fun signature(file: File): VideoSignature {
        val start = System.nanoTime()
        val signature = runBlocking { hasher.signature(file.toURI().toString()) }!!
        Log.i(TAG, "${file.name}: ${signature.durationMs} ms long, signed in ${(System.nanoTime() - start) / 1_000_000} ms")
        return signature
    }

    private fun distances(a: VideoSignature, b: VideoSignature) = a.frames.zip(b.frames, ::hammingDistance)

    @Test
    fun aReencodedVideoMatchesAndOthersDoNot() {
        val original = signature(encode("original.mp4", 640, 360, 4_000_000, keyframeSeconds = 1, frames = 45))
        val reencoded = signature(encode("reencoded.mp4", 320, 180, 300_000, keyframeSeconds = 3, frames = 45))
        val different = signature(encode("different.mp4", 640, 360, 4_000_000, keyframeSeconds = 1, frames = 45, other = true))
        val longer = signature(encode("longer.mp4", 640, 360, 4_000_000, keyframeSeconds = 1, frames = 90))
        Log.i(TAG, "re-encoded frames: ${distances(original, reencoded)}")
        Log.i(TAG, "different frames: ${distances(original, different)}")
        Log.i(TAG, "longer frames: ${distances(original, longer)}")

        assertTrue(durationsMatch(original.durationMs, reencoded.durationMs))
        assertTrue(framesMatch(original, reencoded, threshold))
        assertFalse(framesMatch(original, different, threshold))
        assertFalse(durationsMatch(original.durationMs, longer.durationMs))
        assertEquals(
            listOf(listOf(0L, 1L)),
            groupNearVideos(listOf(0L to original, 1L to reencoded, 2L to different, 3L to longer), threshold),
        )
    }

    @Test
    fun aFileThatIsNotAVideoHasNoSignature() {
        val file = File(dir, "clip.mp4").apply { writeText("not a video") }
        assertNull(runBlocking { hasher.signature(file.toURI().toString()) })
    }

    private companion object {
        const val TAG = "VideoHasherGolden"
        const val FPS = 15
    }
}
