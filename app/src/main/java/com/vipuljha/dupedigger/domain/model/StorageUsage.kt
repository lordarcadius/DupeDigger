package com.vipuljha.dupedigger.domain.model

/**
 * Space on the volumes a scan can reach, summed. [freeBytes] is what this app may actually use, which on Android is
 * a little less than the raw free space, so it matches what the system's storage screen offers.
 */
data class StorageUsage(val totalBytes: Long, val freeBytes: Long) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0L)

    /** 0 when nothing is known, so a meter never divides by zero. */
    fun usedFraction(): Float = if (totalBytes <= 0L) 0f else (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)

    /** The share of the whole volume [bytes] takes, for drawing it inside the used part of a meter. */
    fun fractionOf(bytes: Long): Float = if (totalBytes <= 0L) 0f else (bytes.toFloat() / totalBytes).coerceIn(0f, 1f)
}
