package com.vipuljha.dupedigger.core.common

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class RunSuspendCatchingTest {

    @Test
    fun `returns success with the block's value`() {
        assertEquals(42, runSuspendCatching { 42 }.getOrNull())
    }

    @Test
    fun `wraps ordinary exceptions in a failure`() {
        val error = IOException("disk")
        val result = runSuspendCatching { throw error }
        assertTrue(result.isFailure)
        assertEquals(error, result.exceptionOrNull())
    }

    @Test
    fun `rethrows CancellationException instead of wrapping it`() {
        try {
            runSuspendCatching { throw CancellationException("cancelled") }
            fail("CancellationException was swallowed")
        } catch (_: CancellationException) {
        }
    }

    @Test
    fun `cancelling the caller is not swallowed`() = runTest {
        var reachedAfterCatch = false
        val job = launch {
            runSuspendCatching { awaitCancellation() }
            reachedAfterCatch = true
        }
        testScheduler.runCurrent()
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(reachedAfterCatch)
    }
}
