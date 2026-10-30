package com.vipuljha.dupedigger.domain.detector

/** Below this many words a document carries too little text to compare. */
const val MIN_DOCUMENT_WORDS = 20

/** Bit distance at which two documents' SimHashes count as the same text; 64-bit SimHash near-duplicates sit at 3 or less. */
const val DOCUMENT_SIMHASH_THRESHOLD = 3

private val wordPattern = Regex("[\\p{L}\\p{N}]+")

/** Lower-case runs of letters and digits in any script; punctuation, spacing and layout don't matter. */
fun normalizeWords(text: String): List<String> = wordPattern.findAll(text.lowercase()).map { it.value }.toList()

/** 64-bit SimHash over overlapping 3-word shingles; null when the text has fewer than [MIN_DOCUMENT_WORDS] words. */
fun simHash(text: String): Long? {
    val words = normalizeWords(text)
    if (words.size < MIN_DOCUMENT_WORDS) return null
    val weights = IntArray(64)
    for (i in 0..words.size - 3) {
        val h = stableHash("${words[i]} ${words[i + 1]} ${words[i + 2]}")
        for (bit in 0 until 64) weights[bit] += if ((h ushr bit) and 1L == 1L) 1 else -1
    }
    var result = 0L
    for (bit in 0 until 64) if (weights[bit] > 0) result = result or (1L shl bit)
    return result
}

/** FNV-1a over UTF-8 with a MurmurHash3 finaliser, so every input bit spreads to every output bit; stable across runs. */
fun stableHash(text: String): Long {
    var h = -0x340d631b7bdddcdbL
    for (b in text.toByteArray()) {
        h = h xor (b.toLong() and 0xff)
        h *= 0x100000001b3L
    }
    h = h xor (h ushr 33)
    h *= -0xae502812aa7333L
    h = h xor (h ushr 33)
    h *= -0x3b314601e57a13adL
    return h xor (h ushr 33)
}
