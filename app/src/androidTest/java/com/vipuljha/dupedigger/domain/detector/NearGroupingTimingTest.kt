package com.vipuljha.dupedigger.domain.detector

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vipuljha.dupedigger.domain.model.ImageHashes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

/** P5-02: grouping 50,000 image hashes at the default threshold takes under 5 s on the device. */
@RunWith(AndroidJUnit4::class)
class NearGroupingTimingTest {
    private val count = 50_000
    private val threshold = 10

    private fun Random.flip(value: Long, bits: Int): Long {
        var result = value
        repeat(bits) { result = result xor (1L shl nextInt(64)) }
        return result
    }

    private fun timed(label: String, block: () -> Int): Long {
        val start = System.nanoTime()
        val groups = block()
        val millis = (System.nanoTime() - start) / 1_000_000
        Log.i(TAG, "$label: $millis ms, $groups groups")
        return millis
    }

    @Test
    fun uniformRandomHashes() {
        val random = Random(1)
        val items = List(count) { it.toLong() to ImageHashes(random.nextLong(), random.nextLong()) }

        val millis = timed("uniform 50k") { groupNearImages(items, threshold).size }

        assertTrue("took $millis ms", millis < 5_000)
    }

    @Test
    fun clusteredHashesFormTheirGroups() {
        val random = Random(2)
        val items = (0 until count / 10).flatMap { cluster ->
            val d = random.nextLong()
            val p = random.nextLong()
            List(10) { member -> (cluster * 10L + member) to ImageHashes(random.flip(d, 2), random.flip(p, 2)) }
        }

        var groups = emptyList<List<Long>>()
        val millis = timed("clustered 50k") { groupNearImages(items, threshold).also { groups = it }.size }

        assertTrue("took $millis ms", millis < 5_000)
        assertEquals(count / 10, groups.size)
    }

    private companion object {
        const val TAG = "NearGroupingTiming"
    }
}
