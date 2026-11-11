package com.vipuljha.dupedigger.benchmark

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.BatteryManager
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vipuljha.dupedigger.data.ai.FetchResult
import com.vipuljha.dupedigger.data.ai.LiteRtModelLoader
import com.vipuljha.dupedigger.data.ai.LoadedModel
import com.vipuljha.dupedigger.data.ai.ModelStorage
import com.vipuljha.dupedigger.data.storage.AndroidDeviceSoc
import com.vipuljha.dupedigger.domain.detector.cosine
import com.vipuljha.dupedigger.domain.model.EMBEDDING_DIM
import com.vipuljha.dupedigger.domain.model.EmbeddingBackend
import com.vipuljha.dupedigger.domain.model.EmbeddingInput
import com.vipuljha.dupedigger.domain.model.ModelCapability
import com.vipuljha.dupedigger.domain.model.ModelCatalog
import com.vipuljha.dupedigger.domain.model.ModelVariant
import com.vipuljha.dupedigger.domain.model.SIMILARITY_PROMPT
import com.vipuljha.dupedigger.domain.model.VIDEO_EMBED_FRAMES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * P11-03: what the AI tier costs on each backend this device has — load time, time per item by modality, memory and
 * battery — and whether an NPU build's vectors say the same thing as the portable build's.
 *
 * Backends are loaded through [LiteRtModelLoader] rather than the app's embedder, because the embedder quietly falls
 * back: a figure labelled NPU has to be the NPU, and a backend that can't start has to show up as a failure here.
 *
 * Only when asked, and it downloads models:
 * `./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.embedBenchmark=true`
 * `-Pandroid.testInstrumentationRunnerArguments.class=com.vipuljha.dupedigger.benchmark.EmbeddingBackendBenchmark`
 *
 * Other arguments: `model` (a catalog id, default the 440M text + vision one), `items` (measured items per modality,
 * default [DEFAULT_ITEMS]) and `keep` (`false` deletes the downloaded files afterwards).
 *
 * The NPU row needs two things this build may not have: an ahead-of-time build of the model for this exact chip, and
 * the vendor runtime module, which only a bundle installed from Play carries. Either missing is reported, not hidden.
 */
@RunWith(AndroidJUnit4::class)
class EmbeddingBackendBenchmark {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val args = InstrumentationRegistry.getArguments()
    private val storage = ModelStorage(context, Dispatchers.IO)
    private val soc = AndroidDeviceSoc(context)
    private val loader = LiteRtModelLoader(context, soc)
    private val battery = context.getSystemService(BatteryManager::class.java)

    private val variant = ModelCatalog.byId(args.getString("model") ?: DEFAULT_MODEL)!!
    private val items = args.getString("items")?.toIntOrNull() ?: DEFAULT_ITEMS

    /** One backend's figures; [failure] is set instead of the rest when the backend couldn't start. */
    private class Row(
        val label: String,
        val file: ModelVariant,
        val backend: EmbeddingBackend,
        var failure: String? = null,
        var loadMs: Long = 0,
        val perItemMs: MutableMap<String, Double> = linkedMapOf(),
        var rssMb: Long = 0,
        var microampHours: Long = -1,
        var firstVectors: Map<String, FloatArray> = emptyMap(),
    )

    @Test
    fun backendsCompared() = runBlocking<Unit> {
        assumeTrue(args.getString("embedBenchmark") == "true")
        val npuFile = ModelCatalog.npuBuild(variant, soc.model())
        Log.i(TAG, "chip ${soc.model().ifEmpty { "unreported" }}, model ${variant.id}, $items items per modality, dim $EMBEDDING_DIM")
        Log.i(TAG, if (npuFile == null) "no NPU build published for this chip" else "NPU build ${npuFile.fileName} (${npuFile.sizeBytes / 1_000_000} MB)")

        val rows = listOf(
            Row("CPU", variant, EmbeddingBackend.CPU),
            Row("GPU", variant, EmbeddingBackend.GPU),
            npuFile?.let { Row("NPU", it, EmbeddingBackend.NPU) },
        ).filterNotNull()

        val work = inputs()
        for (row in rows) {
            val path = download(row.file)
            if (path == null) {
                row.failure = "model file unavailable"
                continue
            }
            measure(row, path, work)
            Log.i(TAG, "${row.label}: ${row.failure ?: "done"}")
        }

        report(rows, work.keys.toList())
        agreement(rows, work.keys.toList())
        if (args.getString("keep") == "false") rows.forEach { storage.fetcher.finalFile(it.file).delete() }
    }

    /** One input per modality the model can read, built once so every backend sees identical bytes. */
    private fun inputs(): Map<String, EmbeddingInput> = buildMap {
        put("text", EmbeddingInput.Text(SIMILARITY_PROMPT + LOREM))
        if (ModelCapability.VISION in variant.capabilities) {
            put("image", EmbeddingInput.Image(jpeg(0)))
            put("video", EmbeddingInput.Video(List(VIDEO_EMBED_FRAMES) { jpeg(it) }))
        }
        if (ModelCapability.AUDIO in variant.capabilities) put("audio", EmbeddingInput.Audio(wav()))
    }

    private suspend fun download(file: ModelVariant): String? {
        val installed = storage.fetcher.finalFile(file)
        if (installed.exists() && installed.length() == file.sizeBytes) return installed.path
        Log.i(TAG, "downloading ${file.fileName} (${file.sizeBytes / 1_000_000} MB)…")
        return when (val result = storage.fetcher.fetch(file)) {
            is FetchResult.Done -> result.file.path
            is FetchResult.Failed -> null.also {
                Log.w(TAG, "could not download ${file.fileName}: ${result.reason}${if (result.retryable) " (retryable)" else ""}")
            }
        }
    }

    private fun measure(row: Row, path: String, work: Map<String, EmbeddingInput>) {
        val model = try {
            val start = System.nanoTime()
            loader.load(path, row.file, row.backend).also { row.loadMs = (System.nanoTime() - start) / 1_000_000 }
        } catch (e: Exception) {
            row.failure = "${e::class.simpleName}: ${e.message?.take(160)}"
            return
        }
        try {
            val chargeBefore = charge()
            val first = mutableMapOf<String, FloatArray>()
            for ((modality, input) in work) {
                repeat(WARMUP) { model.embed(input, EMBEDDING_DIM) }
                val start = System.nanoTime()
                repeat(items) { first[modality] = model.embed(input, EMBEDDING_DIM) }
                row.perItemMs[modality] = (System.nanoTime() - start) / 1_000_000.0 / items
            }
            row.firstVectors = first
            // Read while the model is still loaded, and absolute: a delta would carry whatever the last backend left.
            row.rssMb = rssKb() / 1024
            row.microampHours = chargeBefore?.let { before -> charge()?.let { before - it } } ?: -1
        } catch (e: Exception) {
            row.failure = "while embedding — ${e::class.simpleName}: ${e.message?.take(160)}"
        } finally {
            closeQuietly(model)
        }
    }

    private fun report(rows: List<Row>, modalities: List<String>) {
        val header = listOf("backend", "load ms") + modalities.map { "$it ms" } + listOf("RSS MB", "µAh")
        Log.i(TAG, "")
        Log.i(TAG, "=== ${variant.id} on ${soc.model().ifEmpty { "unreported chip" }} ===")
        Log.i(TAG, header.joinToString(" | ") { it.padEnd(COLUMN) })
        for (row in rows) {
            val cells = if (row.failure != null) {
                listOf(row.label, "— ${row.failure}")
            } else {
                listOf(row.label, row.loadMs.toString()) +
                    modalities.map { row.perItemMs[it]?.let { ms -> "%.1f".format(ms) } ?: "-" } +
                    listOf(row.rssMb.toString(), if (row.microampHours >= 0) row.microampHours.toString() else "n/a")
            }
            Log.i(TAG, cells.joinToString(" | ") { it.padEnd(COLUMN) })
        }
        val cpu = rows.firstOrNull { it.label == "CPU" && it.failure == null }
        for (row in rows.filter { it.failure == null && it.label != "CPU" }) {
            if (cpu == null) break
            val speedups = modalities.mapNotNull { modality ->
                val base = cpu.perItemMs[modality] ?: return@mapNotNull null
                val other = row.perItemMs[modality] ?: return@mapNotNull null
                "$modality ${"%.2f".format(base / other)}×"
            }
            Log.i(TAG, "${row.label} against CPU: ${speedups.joinToString(", ")}")
        }
        val ran = rows.filter { it.failure == null }
        if (ran.any { it.microampHours <= 0 }) {
            Log.i(TAG, "µAh needs the device on battery and its charge counter to move during the run; 0 or n/a means it didn't.")
        }
        Log.i(TAG, "RSS is the whole process while that backend was loaded, not the model alone.")
    }

    /**
     * How closely each backend's vector matches the CPU's for the same input. The app stores a build's vectors under
     * its own key on the assumption that two builds can't be compared (D-034); a cosine well under 1 here is that
     * assumption holding, and tells us whether the 0.90 grouping threshold still means the same thing on an NPU build.
     */
    private fun agreement(rows: List<Row>, modalities: List<String>) {
        for (row in rows.filter { it.failure == null }) {
            val health = modalities.mapNotNull { modality ->
                val v = row.firstVectors[modality] ?: return@mapNotNull null
                "$modality ${if (v.all(Float::isFinite)) "norm %.4f".format(norm(v)) else "NOT FINITE"}"
            }
            Log.i(TAG, "${row.label} vectors: ${health.joinToString(", ")}")
        }
        val cpu = rows.firstOrNull { it.label == "CPU" && it.failure == null } ?: return
        for (row in rows.filter { it.failure == null && it.label != "CPU" }) {
            val pairs = modalities.mapNotNull { modality ->
                val a = cpu.firstVectors[modality] ?: return@mapNotNull null
                val b = row.firstVectors[modality] ?: return@mapNotNull null
                if (!a.all(Float::isFinite) || !b.all(Float::isFinite)) "$modality unusable" else "$modality ${"%.4f".format(cosine(a, b))}"
            }
            Log.i(TAG, "${row.label} vector agreement with CPU: ${pairs.joinToString(", ")}")
        }
    }

    private fun norm(v: FloatArray) = kotlin.math.sqrt(v.sumOf { (it * it).toDouble() })

    /** Peak resident set so far, which only grows within a process, so the figure is a delta around one backend. */
    private fun rssKb(): Long = runCatching {
        File("/proc/self/status").readLines().first { it.startsWith("VmRSS:") }.filter(Char::isDigit).toLong()
    }.getOrDefault(0)

    private fun charge(): Long? =
        battery?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.takeIf { it > 0 }?.div(1_000)

    private fun closeQuietly(model: LoadedModel) = runCatching { model.close() }

    /** A different picture per frame, so a video's frames aren't one image repeated. */
    private fun jpeg(frame: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(512, 384, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(30 + frame * 40, 110, 200 - frame * 30))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.YELLOW }
        canvas.drawCircle(120f + frame * 120, 100f, 60f, paint)
        paint.color = Color.rgb(30, 140, 60)
        canvas.drawRect(0f, 280f, 512f, 384f, paint)
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.use { it.toByteArray() }
    }

    /** Ten seconds of 16 kHz mono PCM16, the shape the app's reader produces. */
    private fun wav(): ByteArray {
        val samples = ShortArray(RATE * CLIP_SECONDS) { i ->
            (sin(2 * PI * 440 * i / RATE) * 0.3 * Short.MAX_VALUE).toInt().toShort()
        }
        val data = samples.size * 2
        val buffer = ByteBuffer.allocate(44 + data).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(36 + data).put("WAVE".toByteArray())
        buffer.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
        buffer.put("data".toByteArray()).putInt(data)
        samples.forEach(buffer::putShort)
        return buffer.array()
    }

    private companion object {
        const val TAG = "EmbedBench"
        const val DEFAULT_MODEL = "embeddinggemma-440m-text-vision"
        const val DEFAULT_ITEMS = 16
        const val WARMUP = 2
        const val COLUMN = 10
        const val RATE = 16_000
        const val CLIP_SECONDS = 10
        val LOREM = (1..60).joinToString(" ") { "word$it" }
    }
}
