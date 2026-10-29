package com.vipuljha.dupedigger.benchmark

import android.util.Log
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vipuljha.dupedigger.data.hashing.StreamingContentHasher
import com.vipuljha.dupedigger.data.local.db.DupeDiggerDatabase
import com.vipuljha.dupedigger.data.repository.FileIndexRepositoryImpl
import com.vipuljha.dupedigger.data.storage.FileTreeWalker
import com.vipuljha.dupedigger.domain.model.DiscoveredFile
import com.vipuljha.dupedigger.domain.model.ScanFilter
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.repository.FileEnumerator
import com.vipuljha.dupedigger.domain.usecase.FindExactDuplicatesUseCase
import com.vipuljha.dupedigger.domain.usecase.IndexFilesUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random

/**
 * P7-01 scan throughput: index + exact over 5,000 files of 32 KiB (1,000 duplicated), first scan and an unchanged
 * rescan, in files per second. Runs in the debuggable test build, so the numbers are pessimistic.
 * Only when asked: `-e scanBenchmark true`.
 */
@RunWith(AndroidJUnit4::class)
class ScanThroughputTest {
    @Test
    fun indexAndExact() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("scanBenchmark") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "scan-bench").apply { deleteRecursively(); mkdirs() }
        val random = Random(3)
        val contents = List(FILES - DUPLICATES) { random.nextBytes(FILE_BYTES) }
        repeat(FILES) { i -> File(root, "d${i % 50}").apply { mkdirs() }.resolve("f$i.bin").writeBytes(contents[i % contents.size]) }

        val db = Room.inMemoryDatabaseBuilder(context, DupeDiggerDatabase::class.java).build()
        val repository = FileIndexRepositoryImpl(db, db.fileIndexDao(), db.fileHashDao(), db.perceptualHashDao(), db.embeddingDao())
        val walker = FileTreeWalker { null }
        val enumerator = object : FileEnumerator {
            override fun enumerate(scope: ScanScope, filter: ScanFilter): Flow<DiscoveredFile> = walker.walk(listOf(root), filter)
        }
        val index = IndexFilesUseCase(enumerator, repository)
        val exact = FindExactDuplicatesUseCase(repository, StreamingContentHasher(context, Dispatchers.IO))
        val filter = ScanFilter(emptyList(), skipHidden = false)

        for ((run, scanId) in listOf("first scan" to 1L, "rescan" to 2L)) {
            val t0 = System.nanoTime()
            index(ScanScope.WholeDevice, filter, scanId)
            val t1 = System.nanoTime()
            val result = exact(scanId, minSizeBytes = 1)
            val t2 = System.nanoTime()
            Log.i(TAG, "$run: index ${rate(t1 - t0)} files/s (${(t1 - t0) / 1_000_000} ms), exact ${rate(t2 - t1)} files/s (${(t2 - t1) / 1_000_000} ms), ${result.groups.size} groups")
            assertEquals(DUPLICATES, result.groups.size)
        }
        db.close()
        root.deleteRecursively()
    }

    private fun rate(nanos: Long) = FILES * 1_000_000_000L / nanos.coerceAtLeast(1)

    private companion object {
        const val TAG = "ScanThroughput"
        const val FILES = 5_000
        const val DUPLICATES = 1_000
        const val FILE_BYTES = 32 * 1024
    }
}
