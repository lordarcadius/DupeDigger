package com.vipuljha.dupedigger.domain.detector

import com.vipuljha.dupedigger.domain.model.ImageHashes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class NearGroupingTest {

    private fun hashes(d: Long, p: Long = d) = ImageHashes(d, p)

    @Test
    fun `hamming distance counts differing bits`() {
        assertEquals(0, hammingDistance(0x1234, 0x1234))
        assertEquals(1, hammingDistance(0, 1))
        assertEquals(64, hammingDistance(0, -1))
    }

    @Test
    fun `union-find joins chains and keeps others apart`() {
        val sets = UnionFind(5)
        sets.union(0, 1)
        sets.union(1, 2)
        sets.union(3, 4)

        assertEquals(sets.find(0), sets.find(2))
        assertTrue(sets.find(0) != sets.find(3))
    }

    @Test
    fun `close hashes group, far ones stay apart, and singletons are dropped`() {
        val items = listOf(
            1L to hashes(0L),
            2L to hashes(0b111L),
            3L to hashes(-1L),
            4L to hashes(0x00FF_00FF_00FF_00FFL),
        )

        assertEquals(listOf(listOf(1L, 2L)), groupNearImages(items, threshold = 10))
        assertEquals(emptyList<List<Long>>(), groupNearImages(items, threshold = 2))
    }

    @Test
    fun `both hashes must agree for a match`() {
        val items = listOf(1L to ImageHashes(dHash = 0L, pHash = 0L), 2L to ImageHashes(dHash = -1L, pHash = 1L))

        assertEquals(emptyList<List<Long>>(), groupNearImages(items, threshold = 10))
    }

    @Test
    fun `matches chain transitively into one group`() {
        val items = listOf(1L to hashes(0L), 2L to hashes(0xFFL), 3L to hashes(0xFFFFL))

        assertEquals(listOf(listOf(1L, 2L, 3L)), groupNearImages(items, threshold = 8))
    }

    @Test
    fun `the index finds exactly what comparing every pair finds`() {
        for (threshold in listOf(0, 1, 4, 8, 10, 12, 20, 64)) {
            val random = Random(threshold)
            val items = (0 until 120).flatMap { cluster ->
                val base = random.nextLong()
                List(4) { (cluster * 4L + it) to hashes(base.flipped(random, random.nextInt(0, 14))) }
            }.shuffled(random)

            assertEquals("threshold $threshold", bruteForce(items, threshold), groupNearImages(items, threshold))
            val singles = items.map { (id, h) -> id to h.pHash }
            assertEquals("single, threshold $threshold", bruteForce(items, threshold), groupNearHashes(singles, threshold))
        }
    }

    private fun Long.flipped(random: Random, bits: Int): Long {
        var value = this
        repeat(bits) { value = value xor (1L shl random.nextInt(64)) }
        return value
    }

    private fun bruteForce(items: List<Pair<Long, ImageHashes>>, threshold: Int): List<List<Long>> {
        val sets = UnionFind(items.size)
        for (i in items.indices) for (j in 0 until i) {
            val (a, b) = items[i].second to items[j].second
            if (hammingDistance(a.pHash, b.pHash) <= threshold && hammingDistance(a.dHash, b.dHash) <= threshold) sets.union(i, j)
        }
        return items.indices.groupBy(sets::find).values.filter { it.size > 1 }
            .map { members -> members.map { items[it].first }.sorted() }
            .sortedWith(compareByDescending<List<Long>> { it.size }.thenBy { it.first() })
    }
}
