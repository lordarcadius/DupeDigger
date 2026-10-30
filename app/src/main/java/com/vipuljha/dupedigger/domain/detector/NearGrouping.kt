package com.vipuljha.dupedigger.domain.detector

import com.vipuljha.dupedigger.domain.model.ImageHashes

fun hammingDistance(a: Long, b: Long): Int = (a xor b).countOneBits()

/**
 * Groups items whose perceptual hashes are within [threshold] bits of each other.
 * - Candidate pairs come from a multi-index over pHash; each is confirmed on both pHash and dHash.
 * - Matching is transitive through union-find, so a chain of close images forms one group.
 * Returns groups of two or more ids, each sorted, largest group first.
 */
fun groupNearImages(items: List<Pair<Long, ImageHashes>>, threshold: Int): List<List<Long>> {
    val dHashes = LongArray(items.size) { items[it].second.dHash }
    return groupWithinHamming(items.map { it.first }, LongArray(items.size) { items[it].second.pHash }, threshold) { i, j ->
        hammingDistance(dHashes[i], dHashes[j]) <= threshold
    }
}

/** Groups items whose single 64-bit hashes are within [threshold] bits, like [groupNearImages]. */
fun groupNearHashes(items: List<Pair<Long, Long>>, threshold: Int): List<List<Long>> =
    groupWithinHamming(items.map { it.first }, LongArray(items.size) { items[it].second }, threshold) { _, _ -> true }

/** Union-find over every pair of [hashes] within [threshold] bits that [confirm] also accepts. */
internal inline fun groupWithinHamming(
    ids: List<Long>,
    hashes: LongArray,
    threshold: Int,
    confirm: (Int, Int) -> Boolean,
): List<List<Long>> {
    val index = HammingIndex(hashes, threshold)
    val sets = UnionFind(ids.size)
    val candidates = IntArray(ids.size)
    for (i in ids.indices) {
        for (c in 0 until index.candidates(i, candidates)) {
            val j = candidates[c]
            if (hammingDistance(hashes[i], hashes[j]) <= threshold && confirm(i, j)) sets.union(i, j)
        }
    }
    return ids.indices.groupBy(sets::find).values
        .filter { it.size > 1 }
        .map { members -> members.map { ids[it] }.sorted() }
        .sortedWith(compareByDescending<List<Long>> { it.size }.thenBy { it.first() })
}

/**
 * Multi-index hashing over 64-bit values: finds every earlier value that may be within [threshold] bits.
 * - The bits are split into bands; two values within [threshold] differ by at most `threshold / bands` bits in
 *   some band (pigeonhole), so probing each band's keys within that radius misses no pair.
 * - Small thresholds use more, narrower bands probed at a smaller radius; at most radius 2, a few hundred keys per lookup.
 * - Candidates still need the full distance check.
 */
class HammingIndex(private val values: LongArray, threshold: Int) {
    private val radius = when {
        threshold <= 4 -> 0
        threshold <= 8 -> 1
        else -> 2
    }
    private val bands = (threshold / (radius + 1) + 1).coerceAtMost(64)
    private val widths = IntArray(bands) { 64 / bands + if (it < 64 % bands) 1 else 0 }
    private val offsets = IntArray(bands).also { for (b in 1 until bands) it[b] = it[b - 1] + widths[b - 1] }
    private val buckets = List(bands) { band -> Buckets(LongArray(values.size) { key(values[it], band) }, widths[band]) }
    private val lastSeen = IntArray(values.size) { -1 }

    /** Writes each index below [i] whose value shares a band within the probe radius into [out], once; returns the count. */
    fun candidates(i: Int, out: IntArray): Int {
        var count = 0
        for (band in 0 until bands) {
            val bucket = buckets[band]
            val key = key(values[i], band)
            val width = widths[band]
            count = bucket.collect(key, i, out, count)
            if (radius >= 1) {
                for (a in 0 until width) {
                    val one = key xor (1L shl a)
                    count = bucket.collect(one, i, out, count)
                    if (radius >= 2) for (b in a + 1 until width) count = bucket.collect(one xor (1L shl b), i, out, count)
                }
            }
        }
        return count
    }

    private fun Buckets.collect(key: Long, i: Int, out: IntArray, start: Int): Int {
        val slot = slotOf(key)
        if (slot < 0) return start
        var count = start
        for (k in starts[slot] until starts[slot + 1]) {
            val j = ids[k]
            if (j >= i) break
            if (lastSeen[j] == i) continue
            lastSeen[j] = i
            out[count++] = j
        }
        return count
    }

    private fun key(value: Long, band: Int): Long {
        val width = widths[band]
        val shifted = value ushr offsets[band]
        return if (width == 64) shifted else shifted and ((1L shl width) - 1)
    }
}

/**
 * Item indices grouped by key, in ascending order, with no boxing.
 * - Keys of [width] up to [DIRECT_WIDTH] bits index the table directly; wider keys go through open addressing.
 */
private class Buckets(keys: LongArray, width: Int) {
    private val direct = width <= DIRECT_WIDTH
    private val mask = if (direct) (1 shl width) - 1 else Integer.highestOneBit(maxOf(keys.size, 1) * 2) * 2 - 1
    private val slotKeys = LongArray(if (direct) 0 else mask + 1)
    private val used = BooleanArray(if (direct) 0 else mask + 1)
    val starts = IntArray(mask + 2)
    val ids = IntArray(keys.size)

    init {
        val slots = IntArray(keys.size) { slotFor(keys[it]) }
        for (slot in slots) starts[slot + 1]++
        for (s in 1 until starts.size) starts[s] += starts[s - 1]
        val next = starts.copyOf()
        for (i in keys.indices) ids[next[slots[i]]++] = i
    }

    fun slotOf(key: Long): Int {
        if (direct) return key.toInt()
        var slot = mix(key)
        while (used[slot]) {
            if (slotKeys[slot] == key) return slot
            slot = (slot + 1) and mask
        }
        return -1
    }

    private fun slotFor(key: Long): Int {
        if (direct) return key.toInt()
        var slot = mix(key)
        while (used[slot] && slotKeys[slot] != key) slot = (slot + 1) and mask
        used[slot] = true
        slotKeys[slot] = key
        return slot
    }

    private fun mix(key: Long): Int {
        val h = key * -0x61c8864680b583ebL
        return (h xor (h ushr 32)).toInt() and mask
    }

    private companion object {
        const val DIRECT_WIDTH = 20
    }
}

/** Disjoint sets over 0 until [size], with path halving and union by size. */
class UnionFind(size: Int) {
    private val parent = IntArray(size) { it }
    private val rank = IntArray(size) { 1 }

    fun find(x: Int): Int {
        var node = x
        while (parent[node] != node) {
            parent[node] = parent[parent[node]]
            node = parent[node]
        }
        return node
    }

    fun union(a: Int, b: Int) {
        var rootA = find(a)
        var rootB = find(b)
        if (rootA == rootB) return
        if (rank[rootA] < rank[rootB]) rootA = rootB.also { rootB = rootA }
        parent[rootB] = rootA
        rank[rootA] += rank[rootB]
    }
}
