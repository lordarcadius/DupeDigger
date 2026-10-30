package com.vipuljha.dupedigger.data.hashing

import com.vipuljha.dupedigger.domain.model.ImageHashes
import kotlin.math.PI
import kotlin.math.cos

/**
 * dHash and pHash of a grayscale image given as row-major luminance.
 * - Both start from an area-averaged downscale, so the result barely depends on the source resolution.
 * - dHash: 9 × 8 pixels, one bit per horizontal neighbour pair (left brighter than right).
 * - pHash: 32 × 32 pixels, 2-D DCT, the 8 × 8 lowest frequencies compared with their median (DC left out of the median).
 */
fun perceptualHashes(luma: FloatArray, width: Int, height: Int): ImageHashes {
    require(width > 0 && height > 0 && luma.size == width * height)
    return ImageHashes(
        dHash = differenceHash(areaResize(luma, width, height, 9, 8)),
        pHash = dctHash(areaResize(luma, width, height, DCT_SIZE, DCT_SIZE)),
    )
}

internal fun differenceHash(gray9x8: FloatArray): Long {
    var hash = 0L
    for (y in 0 until 8) for (x in 0 until 8) {
        hash = (hash shl 1) or if (gray9x8[y * 9 + x] > gray9x8[y * 9 + x + 1]) 1L else 0L
    }
    return hash
}

internal fun dctHash(gray32: FloatArray): Long {
    val rows = Array(LOW) { u -> DoubleArray(DCT_SIZE) { y -> (0 until DCT_SIZE).sumOf { x -> gray32[y * DCT_SIZE + x] * COS[u][x] } } }
    val low = DoubleArray(LOW * LOW) { i ->
        val (v, u) = i / LOW to i % LOW
        (0 until DCT_SIZE).sumOf { y -> rows[u][y] * COS[v][y] }
    }
    val median = low.drop(1).sorted().let { (it[it.size / 2 - 1] + it[it.size / 2]) / 2 }
    var hash = 0L
    for (c in low) hash = (hash shl 1) or if (c > median) 1L else 0L
    return hash
}

/** Each target pixel is the mean of the source area it covers, weighting partly covered source pixels. */
internal fun areaResize(src: FloatArray, width: Int, height: Int, targetWidth: Int, targetHeight: Int): FloatArray {
    val out = FloatArray(targetWidth * targetHeight)
    val sx = width.toDouble() / targetWidth
    val sy = height.toDouble() / targetHeight
    for (ty in 0 until targetHeight) {
        val y0 = ty * sy
        val y1 = y0 + sy
        for (tx in 0 until targetWidth) {
            val x0 = tx * sx
            val x1 = x0 + sx
            var sum = 0.0
            var area = 0.0
            for (y in y0.toInt() until minOf(height, kotlin.math.ceil(y1).toInt())) {
                val wy = minOf(y1, y + 1.0) - maxOf(y0, y.toDouble())
                for (x in x0.toInt() until minOf(width, kotlin.math.ceil(x1).toInt())) {
                    val w = wy * (minOf(x1, x + 1.0) - maxOf(x0, x.toDouble()))
                    sum += src[y * width + x] * w
                    area += w
                }
            }
            out[ty * targetWidth + tx] = (sum / area).toFloat()
        }
    }
    return out
}

private const val DCT_SIZE = 32
private const val LOW = 8
private val COS = Array(LOW) { u -> DoubleArray(DCT_SIZE) { x -> cos((2 * x + 1) * u * PI / (2 * DCT_SIZE)) } }
