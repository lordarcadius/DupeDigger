package com.vipuljha.dupedigger.domain.detector

import com.vipuljha.dupedigger.domain.model.VideoSignature
import kotlin.math.max

/** Two durations may differ by 1 s or 2 % of the longer, whichever is more, and still be the same video. */
fun durationsMatch(a: Long, b: Long): Boolean = kotlin.math.abs(a - b) <= durationTolerance(max(a, b))

/** Frame by frame, at least three quarters of the sampled frames are within [threshold] bits. */
fun framesMatch(a: VideoSignature, b: VideoSignature, threshold: Int): Boolean {
    val count = minOf(a.frames.size, b.frames.size)
    if (count == 0) return false
    val close = (0 until count).count { hammingDistance(a.frames[it], b.frames[it]) <= threshold }
    return close * 4 >= count * 3
}

/**
 * Groups videos whose durations and sampled frames match.
 * - Sorted by duration, each video is compared only with those still within the duration tolerance.
 * - Matching is transitive through union-find, like images.
 * Returns groups of two or more ids, each sorted, largest group first.
 */
fun groupNearVideos(items: List<Pair<Long, VideoSignature>>, threshold: Int): List<List<Long>> {
    val sorted = items.sortedBy { it.second.durationMs }
    val sets = UnionFind(sorted.size)
    for (i in sorted.indices) {
        val a = sorted[i].second
        var j = i + 1
        while (j < sorted.size && durationsMatch(a.durationMs, sorted[j].second.durationMs)) {
            if (framesMatch(a, sorted[j].second, threshold)) sets.union(i, j)
            j++
        }
    }
    return sorted.indices.groupBy(sets::find).values
        .filter { it.size > 1 }
        .map { members -> members.map { sorted[it].first }.sorted() }
        .sortedWith(compareByDescending<List<Long>> { it.size }.thenBy { it.first() })
}

private fun durationTolerance(longerMs: Long): Long = max(1_000L, longerMs / 50)
