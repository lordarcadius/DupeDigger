package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.domain.model.AppSettings
import com.vipuljha.dupedigger.domain.model.ContentHashes
import com.vipuljha.dupedigger.domain.model.DiscoveredFile
import com.vipuljha.dupedigger.domain.model.IndexStats
import com.vipuljha.dupedigger.domain.model.IndexedFile
import com.vipuljha.dupedigger.domain.model.MediaAccess
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.ScanFilter
import com.vipuljha.dupedigger.domain.model.ScanFolder
import com.vipuljha.dupedigger.domain.model.ScanProgress
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.ScanStage
import com.vipuljha.dupedigger.domain.model.ScanStatus
import com.vipuljha.dupedigger.domain.model.ScanSummary
import com.vipuljha.dupedigger.domain.model.StorageAccessState
import com.vipuljha.dupedigger.domain.model.exactFingerprint
import com.vipuljha.dupedigger.domain.repository.ContentHasher
import com.vipuljha.dupedigger.domain.repository.ExcludedPathRepository
import com.vipuljha.dupedigger.domain.repository.FileEnumerator
import com.vipuljha.dupedigger.domain.repository.FileIndexRepository
import com.vipuljha.dupedigger.domain.repository.ScanFolderRepository
import com.vipuljha.dupedigger.domain.repository.ScanRunRepository
import com.vipuljha.dupedigger.domain.repository.SettingsRepository
import com.vipuljha.dupedigger.domain.repository.StorageAccess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RunScanUseCaseTest {

    /** sourceUri → content; files sharing content are duplicates. */
    private val disk = linkedMapOf<String, String>()
    private val index = FakeIndex()
    private val hasher = FakeHasher(disk)
    private val runs = FakeScanRuns()
    private val groups = DuplicateGroupUseCasesTest.FakeGroups()
    private val enumerator = FakeEnumerator(disk)
    private val access = MutableStateFlow(StorageAccessState(MediaAccess.FULL, allFiles = true))
    private var settings = AppSettings(minFileSizeBytes = 1, skipHiddenFiles = true)

    private val runScan = RunScanUseCase(
        settings = object : SettingsRepository {
            override val settings: Flow<AppSettings> get() = MutableStateFlow(this@RunScanUseCaseTest.settings)
            override suspend fun update(transform: (AppSettings) -> AppSettings) = Unit
        },
        excludedPaths = object : ExcludedPathRepository {
            override val paths: Flow<ImmutableList<String>> = MutableStateFlow(persistentListOf("/storage/emulated/0/Private"))
            override suspend fun excludeTree(treeUri: String) = Outcome.Success(Unit)
            override suspend fun remove(path: String) = Unit
        },
        storageAccess = object : StorageAccess {
            override fun current() = access.value
            override val mediaPermissions = emptyList<String>()
        },
        scanFolders = object : ScanFolderRepository {
            override val folders: Flow<ImmutableList<ScanFolder>> = MutableStateFlow(persistentListOf())
            override suspend fun add(treeUri: String) = Outcome.Success(Unit)
            override suspend fun remove(treeUri: String) = Unit
        },
        scanRuns = runs,
        indexFiles = IndexFilesUseCase(enumerator, index),
        findExact = FindExactDuplicatesUseCase(index, hasher),
        saveExactGroups = SaveExactGroupsUseCase(groups),
    )

    private fun put(name: String, content: String) {
        disk["file:/storage/emulated/0/$name"] = content
    }

    @Test
    fun `stages report in order and the run completes with its summary`() = runTest {
        put("a.jpg", "photo-1")
        put("b.jpg", "photo-1")
        put("c.jpg", "photo-2")
        put("d.bin", "unique size")
        val progress = mutableListOf<ScanProgress>()

        val outcome = runScan(ScanScope.WholeDevice) { progress += it }

        val summary = (outcome as Outcome.Success).data
        assertEquals(1, summary.exactGroups)
        assertEquals(7L, summary.reclaimableBytes)
        assertEquals(IndexStats(added = 4), summary.index)
        val stages = progress.map { it.stage }
        assertEquals(ScanStage.INDEXING, stages.first())
        assertEquals(ScanStage.EXACT, stages.last())
        assertEquals(stages.sorted(), stages)
        assertEquals(ScanStatus.COMPLETED to summary, runs.runs.single().second to runs.runs.single().third)
    }

    @Test
    fun `a completed scan saves its groups and leaves ignored ones out of the summary`() = runTest {
        put("a.jpg", "photo-1")
        put("b.jpg", "photo-1")
        put("c.mp4", "video-22")
        put("d.mp4", "video-22")
        groups.ignored += exactFingerprint(8, "f:video-22")

        val summary = (runScan(ScanScope.WholeDevice) as Outcome.Success).data

        assertEquals(1, summary.exactGroups)
        assertEquals(7L, summary.reclaimableBytes)
        val (scanId, saved) = groups.saved!!
        assertEquals(runs.runs.single().first, scanId)
        assertEquals(listOf("EXACT:7:f:photo-1"), saved.map { it.fingerprint })
    }

    @Test
    fun `a cancelled scan leaves the previous groups alone`() = runTest {
        repeat(2) { put("v$it.mp4", "video") }
        hasher.blockFullHashesAfter = 0
        val stopped = launch { runScan(ScanScope.WholeDevice) }
        advanceUntilIdle()
        stopped.cancel()
        stopped.join()

        assertEquals(null, groups.saved)
    }

    @Test
    fun `the filter comes from the exclusions and the hidden-files setting`() = runTest {
        settings = settings.copy(skipHiddenFiles = false)
        put("a.jpg", "x")

        runScan(ScanScope.MediaOnly)

        val filter = enumerator.filters.single()
        assertTrue(filter.skips("/storage/emulated/0/Private/a.jpg", "a.jpg"))
        assertFalse(filter.skips("/storage/emulated/0/.thumbs/a.jpg", "a.jpg"))
    }

    @Test
    fun `an unreadable scope fails before touching the index`() = runTest {
        put("a.jpg", "x")
        index.record(listOf(file("file:/old.jpg", "old")), scanId = 0)
        access.value = StorageAccessState(MediaAccess.NONE, allFiles = false)

        val outcome = runScan(ScanScope.MediaOnly)

        assertEquals(DomainError.PermissionDenied, (outcome as Outcome.Failure).error)
        assertTrue(runs.runs.isEmpty())
        assertTrue(enumerator.filters.isEmpty())
        assertEquals(1, index.rows.size)
    }

    @Test
    fun `a stopped scan keeps its hashes and the restart hashes only the rest`() = runTest {
        // Same size and same partial hash, so all six need a full hash.
        repeat(6) { put("v$it.mp4", "video-${it % 3}") }
        hasher.blockFullHashesAfter = 2
        val stopped = launch { runScan(ScanScope.WholeDevice) }
        advanceUntilIdle()
        stopped.cancel()
        stopped.join()
        assertEquals(ScanStatus.CANCELLED, runs.runs.single().second)
        assertEquals(6, hasher.partialCalls)
        assertEquals(2, hasher.fullCalls)

        // A process death leaves its run marked running.
        runs.runs[0] = Triple(runs.runs[0].first, ScanStatus.RUNNING, null)
        hasher.blockFullHashesAfter = Int.MAX_VALUE
        val outcome = runScan(ScanScope.WholeDevice)

        assertEquals(3, (outcome as Outcome.Success).data.exactGroups)
        assertEquals(6, hasher.partialCalls)
        assertEquals(6, hasher.fullCalls)
        assertEquals(listOf(ScanStatus.INTERRUPTED, ScanStatus.COMPLETED), runs.runs.map { it.second })
    }

    @Test
    fun `an enumeration failure fails the run and prunes nothing`() = runTest {
        index.record(listOf(file("file:/old.jpg", "old")), scanId = 0)
        enumerator.failWith = IOException("volume removed")

        val outcome = runScan(ScanScope.WholeDevice)

        assertEquals(DomainError.Io, (outcome as Outcome.Failure).error)
        assertEquals(ScanStatus.FAILED, runs.runs.single().second)
        assertEquals(1, index.rows.size)
    }

    private fun file(uri: String, content: String) =
        DiscoveredFile(uri, uri.removePrefix("file:"), uri.substringAfterLast('/'), null, MediaKind.OTHER, content.length.toLong(), 1, null)

    private class FakeEnumerator(private val disk: Map<String, String>) : FileEnumerator {
        val filters = mutableListOf<ScanFilter>()
        var failWith: Exception? = null

        override fun enumerate(scope: ScanScope, filter: ScanFilter) = flow {
            filters += filter
            failWith?.let { throw it }
            disk.forEach { (uri, content) ->
                emit(DiscoveredFile(uri, uri.removePrefix("file:"), uri.substringAfterLast('/'), null, MediaKind.OTHER, content.length.toLong(), 1, null))
            }
        }
    }

    private class FakeHasher(private val disk: Map<String, String>) : ContentHasher {
        var partialCalls = 0
        var fullCalls = 0
        var blockFullHashesAfter = Int.MAX_VALUE

        override suspend fun partialHash(sourceUri: String, size: Long): Outcome<ContentHashes> {
            partialCalls++
            return Outcome.Success(ContentHashes(partial = "p$size", full = null))
        }

        override suspend fun fullHash(sourceUri: String, size: Long): Outcome<String> {
            if (fullCalls >= blockFullHashesAfter) awaitCancellation()
            fullCalls++
            return Outcome.Success("f:" + disk.getValue(sourceUri))
        }
    }

    private class FakeIndex : FileIndexRepository {
        val rows = linkedMapOf<String, IndexedFile>()
        private val lastSeen = mutableMapOf<String, Long>()
        private var nextId = 1L

        override suspend fun record(files: List<DiscoveredFile>, scanId: Long): IndexStats {
            var added = 0
            var changed = 0
            for (f in files) {
                val row = rows[f.sourceUri]
                when {
                    row == null -> {
                        rows[f.sourceUri] = IndexedFile(nextId++, f.sourceUri, f.size, f.mtime, null, null)
                        added++
                    }
                    row.size != f.size || row.mtime != f.mtime -> {
                        rows[f.sourceUri] = row.copy(size = f.size, mtime = f.mtime, partialHash = null, fullHash = null)
                        changed++
                    }
                }
                lastSeen[f.sourceUri] = scanId
            }
            return IndexStats(added = added, changed = changed, unchanged = files.size - added - changed)
        }

        override suspend fun removeNotSeen(scope: ScanScope, scanId: Long): Int {
            val stale = lastSeen.filterValues { it < scanId }.keys.toList()
            stale.forEach { rows.remove(it); lastSeen.remove(it) }
            return stale.size
        }

        override suspend fun filesSeenIn(scanId: Long) = rows.values.filter { lastSeen[it.sourceUri] == scanId }

        override suspend fun saveHashes(files: List<IndexedFile>) = files.forEach { rows[it.sourceUri] = it }
    }

    private class FakeScanRuns : ScanRunRepository {
        /** id, status, summary */
        val runs = mutableListOf<Triple<Long, ScanStatus, ScanSummary?>>()

        override suspend fun start(scope: ScanScope): Long {
            runs.replaceAll { if (it.second == ScanStatus.RUNNING) it.copy(second = ScanStatus.INTERRUPTED) else it }
            runs += Triple(runs.size + 1L, ScanStatus.RUNNING, null)
            return runs.size.toLong()
        }

        override suspend fun finish(scanId: Long, status: ScanStatus, summary: ScanSummary?) {
            runs[(scanId - 1).toInt()] = Triple(scanId, status, summary)
        }
    }
}
