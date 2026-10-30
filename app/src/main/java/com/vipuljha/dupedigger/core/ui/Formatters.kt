package com.vipuljha.dupedigger.core.ui

import android.text.format.DateUtils
import com.vipuljha.dupedigger.R
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val sizeUnits = listOf(
    R.string.size_b, R.string.size_kb, R.string.size_mb, R.string.size_gb, R.string.size_tb, R.string.size_pb,
)

/** Decimal (base 1000) sizes with the platform's rounding, so a file shows the same size as in system Settings. */
fun formatBytes(bytes: Long): UiText {
    var value = bytes.coerceAtLeast(0L).toFloat()
    var unit = 0
    while (value > 900 && unit < sizeUnits.lastIndex) {
        value /= 1000f
        unit++
    }
    val decimals = when {
        unit == 0 || value >= 10 -> 0
        value < 1 -> 2
        else -> 1
    }
    val number = NumberFormat.getNumberInstance().apply {
        minimumFractionDigits = decimals
        maximumFractionDigits = decimals
        roundingMode = RoundingMode.HALF_UP
    }.format(value.toDouble())
    return UiText.res(sizeUnits[unit], number)
}

fun formatCount(count: Number): String = NumberFormat.getIntegerInstance().format(count)

/** "Just now" under a minute, the platform's relative span up to a week, then the medium date. */
fun formatRelativeTime(epochMillis: Long, nowMillis: Long): UiText {
    val age = nowMillis - epochMillis
    return when {
        age < DateUtils.MINUTE_IN_MILLIS -> UiText.res(R.string.time_just_now)
        age < DateUtils.WEEK_IN_MILLIS ->
            UiText.Raw(DateUtils.getRelativeTimeSpanString(epochMillis, nowMillis, DateUtils.MINUTE_IN_MILLIS).toString())
        else -> UiText.Raw(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())),
        )
    }
}

/** A 0–1 similarity as a whole percentage rounded down and capped at 99%, so a near match never reads as identical. */
fun formatSimilarity(score: Float): UiText {
    val percent = kotlin.math.floor(score.coerceIn(0f, 1f) * 100.0).coerceAtMost(99.0) / 100
    return UiText.Raw(NumberFormat.getPercentInstance().format(percent))
}
