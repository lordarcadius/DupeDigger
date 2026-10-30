package com.vipuljha.dupedigger.domain.detector

import kotlin.math.abs
import kotlin.math.max

/** Clips shorter than this (ringtones, notification sounds, voice notes) are left out of audio matching. */
const val MIN_AUDIO_MS = 30_000L

/** Words that make a title too generic to identify a song ("Track 03", "Recording 12", "Untitled"). */
private val genericTitleWords = setOf("track", "audio", "recording", "rec", "untitled", "unknown", "voice", "song", "ptt", "aud", "new")

/**
 * A key for a song title, from a tag or a file name, so both compare alike.
 * - Leading numbers (track numbers like "01 - ") are dropped.
 * - Null when nothing is left, or every word is generic or contains a digit ("AUD-20240101-WA0003").
 */
fun audioTitleKey(title: String): Long? {
    val words = normalizeWords(title).dropWhile { word -> word.all { it.isDigit() } }
    if (words.isEmpty() || words.all { word -> word in genericTitleWords || word.any(Char::isDigit) }) return null
    return stableHash(words.joinToString(" "))
}

fun audioArtistKey(artist: String): Long? = normalizeWords(artist).takeIf { it.isNotEmpty() }?.let { stableHash(it.joinToString(" ")) }

/** What audio matching compares: duration, the title key and, when known, the artist key. */
data class AudioCandidate(val durationMs: Long, val titleKey: Long, val artistKey: Long?)

/** Durations within 2 s or 1 % of the longer, whichever is more; encoders pad differently. */
fun audioDurationsMatch(a: Long, b: Long): Boolean = abs(a - b) <= max(2_000L, max(a, b) / 100)

/**
 * Groups audio files that look like the same recording: the same title, compatible artists (equal, or unknown on
 * either side) and close durations. Clips under [MIN_AUDIO_MS] never match.
 * Returns groups of two or more ids, each sorted, largest group first.
 */
fun groupNearAudio(items: List<Pair<Long, AudioCandidate>>): List<List<Long>> {
    val eligible = items.filter { it.second.durationMs >= MIN_AUDIO_MS }.sortedBy { it.second.durationMs }
    val sets = UnionFind(eligible.size)
    for ((_, indices) in eligible.indices.groupBy { eligible[it].second.titleKey }) {
        for ((n, i) in indices.withIndex()) {
            val a = eligible[i].second
            for (j in indices.drop(n + 1)) {
                val b = eligible[j].second
                if (!audioDurationsMatch(a.durationMs, b.durationMs)) break
                if (a.artistKey == null || b.artistKey == null || a.artistKey == b.artistKey) sets.union(i, j)
            }
        }
    }
    return eligible.indices.groupBy(sets::find).values
        .filter { it.size > 1 }
        .map { members -> members.map { eligible[it].first }.sorted() }
        .sortedWith(compareByDescending<List<Long>> { it.size }.thenBy { it.first() })
}
