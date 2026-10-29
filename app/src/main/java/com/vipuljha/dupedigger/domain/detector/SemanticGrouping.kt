package com.vipuljha.dupedigger.domain.detector

import java.util.Random
import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.ceil
import kotlin.math.sqrt

/** Dot product of two L2-normalised vectors, which is their cosine similarity. */
fun cosine(a: FloatArray, b: FloatArray): Float {
    var sum = 0f
    for (i in a.indices) sum += a[i] * b[i]
    return sum
}

/**
 * Groups items whose normalised vectors have cosine similarity of at least [threshold], joined transitively with
 * union-find. Returns groups of two or more ids, each sorted, largest group first.
 * - Comparing every pair took 39 s for 20,000 vectors of 256 floats on a desktop CPU, so candidates come from 64-bit
 *   random-hyperplane signatures (the share of differing bits estimates the angle) through the multi-index Hamming
 *   search, and each candidate is confirmed with the exact cosine.
 * - The bit budget allows 2.5 standard deviations above the expected distance at [threshold], so a pair right at the
 *   threshold is missed about 1 % of the time and closer pairs far less often.
 */
fun groupBySimilarity(items: List<Pair<Long, FloatArray>>, threshold: Float): List<List<Long>> {
    if (items.isEmpty()) return emptyList()
    val vectors = items.map { it.second }
    val planes = hyperplanes(vectors.first().size)
    val signatures = LongArray(vectors.size) { signature(vectors[it], planes) }
    return groupWithinHamming(items.map { it.first }, signatures, signatureBudget(threshold)) { i, j ->
        cosine(vectors[i], vectors[j]) >= threshold
    }
}

/** Differing signature bits allowed for a pair at cosine [threshold]. */
internal fun signatureBudget(threshold: Float): Int {
    val p = acos(threshold.coerceIn(-1f, 1f).toDouble()) / PI
    return ceil(SIGNATURE_BITS * p + RECALL_SIGMAS * sqrt(SIGNATURE_BITS * p * (1 - p))).toInt().coerceIn(0, SIGNATURE_BITS)
}

internal fun signature(vector: FloatArray, planes: Array<FloatArray>): Long {
    var bits = 0L
    for (b in planes.indices) if (cosine(vector, planes[b]) >= 0f) bits = bits or (1L shl b)
    return bits
}

private val planeCache = mutableMapOf<Int, Array<FloatArray>>()

/** The same seeded hyperplanes for every call with this [dim], so signatures are comparable. */
private fun hyperplanes(dim: Int): Array<FloatArray> = synchronized(planeCache) {
    planeCache.getOrPut(dim) {
        val random = Random(PLANE_SEED)
        Array(SIGNATURE_BITS) { FloatArray(dim) { random.nextGaussian().toFloat() } }
    }
}

private const val SIGNATURE_BITS = 64
private const val RECALL_SIGMAS = 2.5
private const val PLANE_SEED = 0x5eed_0f_d1ffL
