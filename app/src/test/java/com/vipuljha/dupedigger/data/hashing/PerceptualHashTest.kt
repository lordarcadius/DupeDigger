package com.vipuljha.dupedigger.data.hashing

import com.vipuljha.dupedigger.domain.detector.hammingDistance
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin
import kotlin.random.Random

class PerceptualHashTest {

    /** A smooth synthetic scene with a few soft blobs, sampled at any resolution. */
    private fun scene(width: Int, height: Int, seed: Int): FloatArray {
        val random = Random(seed)
        val blobs = List(6) { Triple(random.nextDouble(), random.nextDouble(), random.nextDouble(40.0, 200.0)) }
        return FloatArray(width * height) { i ->
            val (x, y) = (i % width).toDouble() / width to (i / width).toDouble() / height
            val base = 128 + 40 * sin(6 * x + seed) * sin(4 * y)
            blobs.fold(base) { v, (bx, by, level) -> v + level * kotlin.math.exp(-((x - bx) * (x - bx) + (y - by) * (y - by)) * 40) }
                .coerceIn(0.0, 255.0).toFloat()
        }
    }

    @Test
    fun `area resize averages whole and partial pixels`() {
        val src = floatArrayOf(0f, 2f, 4f, 6f)
        assertArrayEquals(floatArrayOf(1f, 5f), areaResize(src, 4, 1, 2, 1), 1e-5f)
        assertArrayEquals(floatArrayOf(3f), areaResize(src, 4, 1, 1, 1), 1e-5f)
    }

    @Test
    fun `dHash sets a bit where the left pixel is brighter`() {
        val falling = FloatArray(72) { 9f - it % 9 }
        assertEquals(-1L, differenceHash(falling))
        assertEquals(0L, differenceHash(FloatArray(72) { (it % 9).toFloat() }))
    }

    @Test
    fun `the same scene at another resolution hashes almost the same`() {
        val big = perceptualHashes(scene(400, 300, seed = 1), 400, 300)
        val small = perceptualHashes(scene(97, 73, seed = 1), 97, 73)

        assertTrue(hammingDistance(big.pHash, small.pHash) <= 6)
        assertTrue(hammingDistance(big.dHash, small.dHash) <= 6)
    }

    @Test
    fun `brightness change and noise keep the hashes close`() {
        val random = Random(9)
        val original = scene(200, 150, seed = 3)
        val edited = FloatArray(original.size) { (original[it] * 0.9f + 12 + random.nextFloat() * 6).coerceIn(0f, 255f) }

        val a = perceptualHashes(original, 200, 150)
        val b = perceptualHashes(edited, 200, 150)
        assertTrue(hammingDistance(a.pHash, b.pHash) <= 6)
        assertTrue(hammingDistance(a.dHash, b.dHash) <= 6)
    }

    @Test
    fun `a different scene is far away`() {
        val a = perceptualHashes(scene(200, 150, seed = 4), 200, 150)
        val b = perceptualHashes(scene(200, 150, seed = 11), 200, 150)

        assertTrue(hammingDistance(a.pHash, b.pHash) > 10)
    }
}
