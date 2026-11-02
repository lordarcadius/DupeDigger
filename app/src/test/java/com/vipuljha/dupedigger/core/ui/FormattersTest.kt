package com.vipuljha.dupedigger.core.ui

import com.vipuljha.dupedigger.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class FormattersTest {
    private val saved = Locale.getDefault()

    @Before
    fun setUp() = Locale.setDefault(Locale.US)

    @After
    fun tearDown() = Locale.setDefault(saved)

    @Test
    fun `sizes use base 1000 with the platform's decimals`() {
        assertEquals(UiText.res(R.string.size_b, "512"), formatBytes(512))
        assertEquals(UiText.res(R.string.size_kb, "0.95"), formatBytes(950))
        assertEquals(UiText.res(R.string.size_mb, "4.2"), formatBytes(4_200_000))
        assertEquals(UiText.res(R.string.size_mb, "50"), formatBytes(50_000_000))
        assertEquals(UiText.res(R.string.size_gb, "1.2"), formatBytes(1_234_567_890))
        assertEquals(UiText.res(R.string.size_b, "0"), formatBytes(-5))
    }

    @Test
    fun `a time under a minute old is just now`() {
        assertEquals(UiText.res(R.string.time_just_now), formatRelativeTime(1_000, 30_000))
    }

    @Test
    fun `durations round down to the largest two units`() {
        assertEquals(UiText.res(R.string.duration_under_second), formatDuration(999))
        assertEquals(UiText.res(R.string.duration_seconds, "42"), formatDuration(42_900))
        assertEquals(UiText.res(R.string.duration_minutes, "2", "5"), formatDuration(125_000))
        assertEquals(UiText.res(R.string.duration_hours, "1", "1"), formatDuration(3_661_000))
        assertEquals(UiText.res(R.string.duration_under_second), formatDuration(-5))
    }
}
