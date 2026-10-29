package com.vipuljha.dupedigger.domain.detector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt
import kotlin.random.Random

class SemanticGroupingTest {
    private fun unit(v: FloatArray): FloatArray {
        val n = sqrt(v.sumOf { (it * it).toDouble() }).toFloat()
        return FloatArray(v.size) { v[it] / n }
    }

    /** [clusters] groups of [size] vectors, noise [noise] around a random centre, plus [singles] unrelated vectors. */
    private fun data(seed: Int, clusters: Int, size: Int, noise: Float, singles: Int, dim: Int = 256): List<Pair<Long, FloatArray>> {
        val r = Random(seed)
        var id = 0L
        val grouped = (0 until clusters).flatMap {
            val base = FloatArray(dim) { r.nextFloat() - 0.5f }
            List(size) { id++ to unit(FloatArray(dim) { base[it] + (r.nextFloat() - 0.5f) * noise }) }
        }
        return grouped + List(singles) { id++ to unit(FloatArray(dim) { r.nextFloat() - 0.5f }) }
    }

    private fun allPairs(items: List<Pair<Long, FloatArray>>, threshold: Float): List<List<Long>> {
        val sets = UnionFind(items.size)
        for (i in items.indices) for (j in 0 until i) if (cosine(items[i].second, items[j].second) >= threshold) sets.union(i, j)
        return items.indices.groupBy(sets::find).values.filter { it.size > 1 }.map { m -> m.map { items[it].first }.sorted() }
            .sortedWith(compareByDescending<List<Long>> { it.size }.thenBy { it.first() })
    }

    @Test
    fun `the bit budget grows as the threshold loosens`() {
        assertEquals(17, signatureBudget(0.9f))
        assertTrue(signatureBudget(0.8f) > signatureBudget(0.9f))
        assertTrue(signatureBudget(0.99f) < signatureBudget(0.9f))
    }

    @Test
    fun `close vectors group and far ones stay apart`() {
        val a = unit(FloatArray(256) { if (it < 128) 1f else 0f })
        val near = unit(FloatArray(256) { if (it < 128) 1f else if (it < 140) 0.4f else 0f })
        val far = unit(FloatArray(256) { if (it >= 128) 1f else 0f })
        assertTrue(cosine(a, near) >= 0.9f)

        assertEquals(listOf(listOf(1L, 2L)), groupBySimilarity(listOf(1L to a, 2L to near, 3L to far), 0.9f))
    }

    @Test
    fun `the index finds the same groups as comparing every pair`() {
        for ((seed, noise) in listOf(1 to 0.25f, 2 to 0.35f, 3 to 0.45f)) {
            val items = data(seed, clusters = 150, size = 3, noise = noise, singles = 300).shuffled(Random(seed))
            assertEquals("noise $noise", allPairs(items, 0.9f), groupBySimilarity(items, 0.9f))
        }
    }

    @Test
    fun `20,000 vectors group in well under 10 s`() {
        val items = data(7, clusters = 4_000, size = 5, noise = 0.25f, singles = 0)
        val start = System.nanoTime()
        val groups = groupBySimilarity(items, 0.9f)
        val millis = (System.nanoTime() - start) / 1_000_000
        println("20k vectors: $millis ms, ${groups.size} groups")
        assertEquals(4_000, groups.size)
        assertTrue("took $millis ms", millis < 10_000)
    }
}
