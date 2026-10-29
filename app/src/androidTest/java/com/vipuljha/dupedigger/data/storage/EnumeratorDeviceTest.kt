package com.vipuljha.dupedigger.data.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vipuljha.dupedigger.domain.model.ScanFilter
import com.vipuljha.dupedigger.domain.model.ScanScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.io.File

/** P2-03 on a real device: 50k files without memory growth, and cancellation within a second. */
@RunWith(AndroidJUnit4::class)
class EnumeratorDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "enumerator-test")
    private val walker = FileTreeWalker { null }
    private val keepAll = ScanFilter(emptyList(), skipHidden = false)

    @Before
    fun createTree() {
        root.deleteRecursively()
        repeat(500) { d ->
            val dir = File(root, "d$d").apply { mkdirs() }
            repeat(100) { f -> File(dir, "f$f.bin").createNewFile() }
        }
    }

    @After
    fun deleteTree() {
        root.deleteRecursively()
    }

    @Test
    fun walks50kFilesWithoutHeapGrowth() = runBlocking {
        val runtime = Runtime.getRuntime()
        System.gc()
        val before = runtime.totalMemory() - runtime.freeMemory()
        var peak = before
        var count = 0
        val started = System.nanoTime()
        walker.walk(listOf(root), keepAll).collect {
            if (++count % 1_000 == 0) peak = maxOf(peak, runtime.totalMemory() - runtime.freeMemory())
        }
        val millis = (System.nanoTime() - started) / 1_000_000
        val growthMb = (peak - before) / (1024 * 1024)
        Timber.i("Enumerated %d files in %d ms, peak heap growth %d MB of %d MB max", count, millis, growthMb, runtime.maxMemory() / (1024 * 1024))

        assertEquals(50_000, count)
        assertTrue("heap grew by $growthMb MB", growthMb < 64)
    }

    @Test
    fun cancellingStopsWithinASecond() = runBlocking {
        var seen = 0
        val job = launch(Dispatchers.IO) { walker.walk(listOf(root), keepAll).collect { seen++; Thread.sleep(1) } }
        delay(300)
        val started = System.nanoTime()
        job.cancelAndJoin()
        val stopMillis = (System.nanoTime() - started) / 1_000_000
        Timber.i("Cancelled after %d files; stopped in %d ms", seen, stopMillis)

        assertTrue("stopped after $stopMillis ms", stopMillis < 1_000)
        assertTrue("walk was cut short at $seen files", seen in 1 until 50_000)
    }

    @Test
    fun mediaStoreEnumerationRuns() = runBlocking {
        val count = AndroidFileEnumerator(context, Dispatchers.IO).enumerate(ScanScope.MediaOnly, keepAll).count()
        Timber.i("MediaStore returned %d media files", count)
        assertTrue(count >= 0)
    }
}
