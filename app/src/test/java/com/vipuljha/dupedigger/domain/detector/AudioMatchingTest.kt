package com.vipuljha.dupedigger.domain.detector

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioMatchingTest {

    @Test
    fun `a tag title and a file name with a track number give the same key`() {
        assertEquals(audioTitleKey("Night Drive"), audioTitleKey("02 - night drive"))
        assertEquals(audioTitleKey("Night Drive"), audioTitleKey("01. NIGHT-DRIVE"))
    }

    @Test
    fun `generic titles have no key`() {
        assertNull(audioTitleKey("Track 05"))
        assertNull(audioTitleKey("Recording 12"))
        assertNull(audioTitleKey("AUD-20240101-WA0003"))
        assertNull(audioTitleKey("07"))
    }

    @Test
    fun `durations may differ by 2 s or 1 percent of the longer`() {
        assertTrue(audioDurationsMatch(180_000, 182_000))
        assertFalse(audioDurationsMatch(180_000, 182_001))
        assertTrue(audioDurationsMatch(600_000, 606_000))
    }

    private val night = audioTitleKey("Night Drive")!!

    @Test
    fun `same title and length group, with an unknown artist on one side`() {
        val items = listOf(
            1L to AudioCandidate(200_000, night, audioArtistKey("The Band")),
            2L to AudioCandidate(200_400, night, null),
            3L to AudioCandidate(200_000, night, audioArtistKey("Someone Else")),
            4L to AudioCandidate(260_000, night, audioArtistKey("The Band")),
            5L to AudioCandidate(200_000, audioTitleKey("Day Drive")!!, audioArtistKey("The Band")),
        )

        assertEquals(listOf(listOf(1L, 2L, 3L)), groupNearAudio(items))
    }

    @Test
    fun `short clips never match`() {
        val items = listOf(1L to AudioCandidate(10_000, night, null), 2L to AudioCandidate(10_000, night, null))
        assertEquals(emptyList<List<Long>>(), groupNearAudio(items))
    }
}
