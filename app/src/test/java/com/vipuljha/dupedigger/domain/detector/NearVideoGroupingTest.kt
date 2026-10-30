package com.vipuljha.dupedigger.domain.detector

import com.vipuljha.dupedigger.domain.model.VideoSignature
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearVideoGroupingTest {

    private val frames = List(8) { it * 0x1111_1111L }

    /** [far] frames get all 64 bits flipped; the rest stay as they are. */
    private fun clip(durationMs: Long, far: Int = 0) =
        VideoSignature(durationMs, frames.mapIndexed { i, f -> if (i < far) f.inv() else f }.toImmutableList())

    @Test
    fun `durations may differ by 1 s or 2 percent of the longer, whichever is more`() {
        assertTrue(durationsMatch(10_000, 11_000))
        assertFalse(durationsMatch(10_000, 11_001))
        assertTrue(durationsMatch(100_000, 102_000))
        assertFalse(durationsMatch(100_000, 102_100))
    }

    @Test
    fun `three quarters of the frames must be close`() {
        assertTrue(framesMatch(clip(1_000), clip(1_000, far = 2), threshold = 10))
        assertFalse(framesMatch(clip(1_000), clip(1_000, far = 3), threshold = 10))
    }

    @Test
    fun `matching videos group, and a different length or picture stays apart`() {
        val items = listOf(
            1L to clip(60_000),
            2L to clip(60_800, far = 1),
            3L to clip(120_000),
            4L to clip(60_000, far = 8),
        )

        assertEquals(listOf(listOf(1L, 2L)), groupNearVideos(items, threshold = 10))
    }

    @Test
    fun `a chain within the tolerance joins one group`() {
        val items = listOf(1L to clip(10_000), 2L to clip(10_900), 3L to clip(11_800))

        assertEquals(listOf(listOf(1L, 2L, 3L)), groupNearVideos(items, threshold = 10))
    }
}
