package com.vipuljha.dupedigger.domain.detector

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.sqrt
import kotlin.random.Random

/** P6-04: 20,000 vectors of 256 floats group in under 10 s on the device. */
@RunWith(AndroidJUnit4::class)
class SemanticGroupingTimingTest {
    @Test
    fun twentyThousandVectors() {
        val r = Random(7)
        val items = (0 until 4_000).flatMap { c ->
            val base = FloatArray(256) { r.nextFloat() - 0.5f }
            List(5) { k ->
                val v = FloatArray(256) { base[it] + (r.nextFloat() - 0.5f) * 0.25f }
                val n = sqrt(v.sumOf { (it * it).toDouble() }).toFloat()
                (c * 5L + k) to FloatArray(256) { v[it] / n }
            }
        }
        val start = System.nanoTime()
        val groups = groupBySimilarity(items, 0.9f)
        val millis = (System.nanoTime() - start) / 1_000_000
        Log.i("SemanticTiming", "20k vectors: $millis ms, ${groups.size} groups")
        assertEquals(4_000, groups.size)
        assertTrue("took $millis ms", millis < 10_000)
    }
}
