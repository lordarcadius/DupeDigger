package com.vipuljha.dupedigger.data.hashing

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vipuljha.dupedigger.domain.detector.AudioCandidate
import com.vipuljha.dupedigger.domain.detector.DOCUMENT_SIMHASH_THRESHOLD
import com.vipuljha.dupedigger.domain.detector.audioArtistKey
import com.vipuljha.dupedigger.domain.detector.audioTitleKey
import com.vipuljha.dupedigger.domain.detector.groupNearAudio
import com.vipuljha.dupedigger.domain.detector.hammingDistance
import com.vipuljha.dupedigger.domain.detector.simHash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import kotlin.math.PI
import kotlin.math.sin

/**
 * P5-04: two PDFs made from the same text with different page setups (so different bytes) match; another text doesn't.
 * P5-05: the same song as a tagged MP3 and an untagged M4A named after it is a candidate; another title or length isn't.
 */
@RunWith(AndroidJUnit4::class)
class DocumentAudioGoldenTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, "golden-docs")
    private val textReader = AndroidDocumentTextReader(context, Dispatchers.IO)
    private val tagReader = AndroidAudioTagReader(context, Dispatchers.IO)

    @Before
    fun setUp() {
        dir.deleteRecursively()
        dir.mkdirs()
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private val report = """
        The quarterly review covers revenue, hiring and the roadmap for the next two quarters. Revenue grew in every
        region, led by the enterprise segment, while consumer sales stayed flat after the spring promotion ended.
        Hiring slowed as planned, with most new roles in support and infrastructure. The roadmap keeps the offline
        mode and the new billing pages, and moves the analytics rebuild to the autumn so the team can finish the
        migration of the reporting database before the holiday freeze begins in December.
    """.trimIndent()

    private val recipe = """
        Preheat the oven and butter a deep tin. Whisk the eggs with the sugar until pale, fold in the flour and the
        melted butter, then the lemon zest. Bake for forty minutes until a skewer comes out clean, cool on a rack and
        dust with icing sugar before serving with cream and fresh berries from the garden.
    """.trimIndent()

    /** [text] eight words to a line, on pages of [width] × [height] points with a [margin]. */
    private fun pdf(name: String, text: String, width: Int, height: Int, margin: Float): File {
        val document = PdfDocument()
        val page = document.startPage(PdfDocument.PageInfo.Builder(width, height, 1).create())
        val paint = Paint().apply { textSize = 11f }
        text.split(Regex("\\s+")).chunked(8).forEachIndexed { i, line -> page.canvas.drawText(line.joinToString(" "), margin, margin + 16f * (i + 1), paint) }
        document.finishPage(page)
        return File(dir, name).apply { outputStream().use { document.writeTo(it) }; document.close() }
    }

    private fun sha(file: File) = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }

    private fun pdfSimHash(file: File): Long {
        assertTrue(textReader.supports(file.name, "application/pdf"))
        val text = runBlocking { textReader.text(file.toURI().toString(), file.name, "application/pdf") }!!
        return simHash(text)!!
    }

    @Test
    fun pdfsOfTheSameTextMatchAndOtherTextDoesNot() {
        val a4 = pdf("report-a4.pdf", report, 595, 842, 50f)
        val letter = pdf("report-letter.pdf", report, 612, 792, 72f)
        val other = pdf("recipe.pdf", recipe, 595, 842, 50f)
        val template = "Dear %s, thank you for your order. Your invoice for %s is attached. $report"
        val alice = pdf("alice.pdf", template.format("Alice", "120 euros"), 595, 842, 50f)
        val bob = pdf("bob.pdf", template.format("Bob", "340 euros"), 595, 842, 50f)
        assertNotEquals(sha(a4), sha(letter))

        val same = hammingDistance(pdfSimHash(a4), pdfSimHash(letter))
        val different = hammingDistance(pdfSimHash(a4), pdfSimHash(other))
        val letters = hammingDistance(pdfSimHash(alice), pdfSimHash(bob))
        Log.i(TAG, "same text, other page setup: $same; other text: $different; template letters: $letters")
        assertTrue("same text $same", same <= DOCUMENT_SIMHASH_THRESHOLD)
        assertTrue("other text $different", different > DOCUMENT_SIMHASH_THRESHOLD)
    }

    @Test
    fun markdownCountsAsADocumentAndIsRead() {
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension("md")
        Log.i(TAG, "md mime: $mime")
        val markdown = "# Notes\n\n$report"
        val file = File(dir, "notes.md").apply { writeText(markdown) }
        assertTrue(textReader.supports(file.name, mime))
        assertEquals(simHash(markdown), simHash(runBlocking { textReader.text(file.toURI().toString(), file.name, mime) }!!))
    }

    /** An MP3 of [seconds] of silent 128 kbps frames, with an ID3v2.3 tag holding [title] and [artist]. */
    private fun mp3(name: String, seconds: Int, title: String, artist: String): File {
        fun textFrame(id: String, value: String): ByteArray {
            val body = byteArrayOf(0) + value.toByteArray(Charsets.ISO_8859_1)
            val size = body.size
            return id.toByteArray() + byteArrayOf((size ushr 24).toByte(), (size ushr 16).toByte(), (size ushr 8).toByte(), size.toByte(), 0, 0) + body
        }
        val frames = textFrame("TIT2", title) + textFrame("TPE1", artist)
        val n = frames.size
        val header = "ID3".toByteArray() + byteArrayOf(3, 0, 0, (n ushr 21 and 0x7f).toByte(), (n ushr 14 and 0x7f).toByte(), (n ushr 7 and 0x7f).toByte(), (n and 0x7f).toByte())
        val out = ByteArrayOutputStream().apply { write(header); write(frames) }
        val frame = ByteArray(417).apply { this[0] = 0xFF.toByte(); this[1] = 0xFB.toByte(); this[2] = 0x90.toByte(); this[3] = 0xC0.toByte() }
        repeat(seconds * 44_100 / 1152) { out.write(frame) }
        return File(dir, name).apply { writeBytes(out.toByteArray()) }
    }

    /** An untagged AAC .m4a of [seconds] of a 440 Hz tone. */
    private fun m4a(name: String, seconds: Int): File {
        val file = File(dir, name)
        val rate = 44_100
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, rate, 1).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, 64_000)
        }
        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        codec.start()
        val muxer = MediaMuxer(file.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val info = MediaCodec.BufferInfo()
        val total = rate.toLong() * seconds
        var sample = 0L
        var track = -1
        var inputDone = false
        while (true) {
            if (!inputDone) {
                val index = codec.dequeueInputBuffer(10_000)
                if (index >= 0) {
                    val buffer = codec.getInputBuffer(index)!!
                    val count = minOf(buffer.capacity() / 2, (total - sample).toInt())
                    for (k in 0 until count) buffer.putShort((sin(2 * PI * 440 * (sample + k) / rate) * 8000).toInt().toShort())
                    val timeUs = sample * 1_000_000 / rate
                    sample += count
                    inputDone = sample >= total
                    codec.queueInputBuffer(index, 0, count * 2, timeUs, if (inputDone) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0)
                }
            }
            val out = codec.dequeueOutputBuffer(info, 10_000)
            if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                track = muxer.addTrack(codec.outputFormat)
                muxer.start()
            } else if (out >= 0) {
                if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0 && info.size > 0) muxer.writeSampleData(track, codec.getOutputBuffer(out)!!, info)
                codec.releaseOutputBuffer(out, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        }
        codec.stop()
        codec.release()
        muxer.stop()
        muxer.release()
        return file
    }

    @Test
    fun theSameSongAsMp3AndM4aIsACandidate() {
        val files = listOf(
            mp3("rip-0042.mp3", 40, "Night Drive", "The Band"),
            m4a("02 - Night Drive.m4a", 40),
            mp3("other.mp3", 40, "Day Drive", "The Band"),
            mp3("night-long.mp3", 60, "Night Drive", "The Band"),
        )
        val candidates = files.mapIndexed { i, file ->
            val tags = runBlocking { tagReader.read(file.toURI().toString()) }!!
            Log.i(TAG, "${file.name}: ${tags.durationMs} ms, title ${tags.title}, artist ${tags.artist}")
            i.toLong() to AudioCandidate(
                tags.durationMs,
                audioTitleKey(tags.title ?: file.name.substringBeforeLast('.'))!!,
                tags.artist?.let(::audioArtistKey),
            )
        }
        val mp3Tags = runBlocking { tagReader.read(files[0].toURI().toString()) }!!
        assertEquals("Night Drive" to "The Band", mp3Tags.title to mp3Tags.artist)
        assertNull(runBlocking { tagReader.read(files[1].toURI().toString()) }!!.title)

        assertEquals(listOf(listOf(0L, 1L)), groupNearAudio(candidates))
    }

    private companion object {
        const val TAG = "DocAudioGolden"
    }
}
