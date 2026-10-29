package com.vipuljha.dupedigger.data.hashing

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.media.ExifInterface
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vipuljha.dupedigger.domain.detector.groupNearImages
import com.vipuljha.dupedigger.domain.detector.hammingDistance
import com.vipuljha.dupedigger.domain.model.ImageHashes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

/** P5-01 golden set: one photo resized, recompressed, converted or EXIF-rotated still matches; another photo doesn't. */
@RunWith(AndroidJUnit4::class)
class ImageHasherGoldenTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, "golden")
    private val hasher = AndroidImageHasher(context, Dispatchers.IO)
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

    /** A busy scene: a gradient sky, then seeded shapes; [stripes] changes its structure, not only its colours. */
    private fun scene(seed: Int, stripes: Boolean): Bitmap {
        val bitmap = Bitmap.createBitmap(1200, 900, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val random = Random(seed)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, 900f, Color.rgb(40, 90, 200), Color.rgb(250, 220, 160), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, 1200f, 900f, paint)
        paint.shader = null
        if (stripes) {
            for (x in 0 until 1200 step 150) {
                paint.color = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
                canvas.drawRect(x.toFloat(), 0f, x + 70f, 900f, paint)
            }
        }
        repeat(25) {
            paint.color = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
            val (x, y, r) = Triple(random.nextFloat() * 1200, random.nextFloat() * 900, 30 + random.nextFloat() * 150)
            if (random.nextBoolean()) canvas.drawCircle(x, y, r, paint) else canvas.drawRect(x, y, x + r * 1.5f, y + r, paint)
        }
        return bitmap
    }

    private fun save(bitmap: Bitmap, name: String, format: Bitmap.CompressFormat, quality: Int): File =
        File(dir, name).apply { outputStream().use { bitmap.compress(format, quality, it) } }

    private fun hash(file: File): ImageHashes = runBlocking { hasher.hash(file.toURI().toString())!! }

    @Test
    fun variantsOfOnePhotoMatchAndAnotherPhotoDoesNot() {
        val photo = scene(seed = 7, stripes = false)
        val original = save(photo, "original.jpg", Bitmap.CompressFormat.JPEG, 92)
        val variants = mapOf(
            "resized" to save(Bitmap.createScaledBitmap(photo, 480, 360, true), "resized.jpg", Bitmap.CompressFormat.JPEG, 90),
            "recompressed" to save(BitmapFactory.decodeFile(original.path), "recompressed.jpg", Bitmap.CompressFormat.JPEG, 40),
            "png" to save(photo, "copy.png", Bitmap.CompressFormat.PNG, 100),
            "webp" to save(photo, "copy.webp", Bitmap.CompressFormat.WEBP_LOSSY, 75),
            "exif-rotated" to save(
                Bitmap.createBitmap(photo, 0, 0, photo.width, photo.height, Matrix().apply { postRotate(-90f) }, true),
                "rotated.jpg", Bitmap.CompressFormat.JPEG, 92,
            ).also { file ->
                ExifInterface(file.path).apply {
                    setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
                    saveAttributes()
                }
            },
        )
        val different = save(scene(seed = 99, stripes = true), "different.jpg", Bitmap.CompressFormat.JPEG, 92)

        val base = hash(original)
        for ((name, file) in variants) {
            val h = hash(file)
            val (dp, dd) = hammingDistance(base.pHash, h.pHash) to hammingDistance(base.dHash, h.dHash)
            Log.i(TAG, "$name: pHash $dp, dHash $dd")
            assertTrue("$name pHash $dp", dp <= threshold)
            assertTrue("$name dHash $dd", dd <= threshold)
        }
        val other = hash(different)
        val dOther = hammingDistance(base.pHash, other.pHash)
        Log.i(TAG, "different: pHash $dOther, dHash ${hammingDistance(base.dHash, other.dHash)}")
        assertTrue("different pHash $dOther", dOther > threshold)

        val all = (listOf(original) + variants.values + different).mapIndexed { i, f -> i.toLong() to hash(f) }
        assertEquals(listOf((0L..5L).toList()), groupNearImages(all, threshold))
    }

    @Test
    fun aFileThatIsNotAnImageHashesToNull() {
        val file = File(dir, "notes.jpg").apply { writeText("not an image") }
        assertNull(runBlocking { hasher.hash(file.toURI().toString()) })
    }

    private companion object {
        const val TAG = "ImageHasherGolden"
    }
}
