package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.domain.model.AudioFile
import com.vipuljha.dupedigger.domain.model.AudioSignature
import com.vipuljha.dupedigger.domain.model.DiscoveredFile
import com.vipuljha.dupedigger.domain.model.DocumentFile
import com.vipuljha.dupedigger.domain.model.EmbeddedFile
import com.vipuljha.dupedigger.domain.model.ImageFile
import com.vipuljha.dupedigger.domain.model.ImageHashes
import com.vipuljha.dupedigger.domain.model.IndexStats
import com.vipuljha.dupedigger.domain.model.IndexedFile
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.ScanFilter
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.StoredFile
import com.vipuljha.dupedigger.domain.model.VideoFile
import com.vipuljha.dupedigger.domain.model.VideoSignature
import com.vipuljha.dupedigger.domain.repository.FileEnumerator
import com.vipuljha.dupedigger.domain.repository.FileIndexRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class IndexFilesUseCaseTest {

    private val filter = ScanFilter(emptyList(), skipHidden = false)

    private fun file(i: Int) = DiscoveredFile("file:/f$i", "/f$i", "f$i", null, MediaKind.OTHER, 1, 1, null)

    private class FakeEnumerator(private val files: Flow<DiscoveredFile>) : FileEnumerator {
        override fun enumerate(scope: ScanScope, filter: ScanFilter) = files
    }

    private class RecordingRepository : FileIndexRepository {
        val batches = mutableListOf<List<DiscoveredFile>>()
        val prunes = mutableListOf<Pair<ScanScope, Long>>()

        override suspend fun record(files: List<DiscoveredFile>, scanId: Long): IndexStats {
            batches += files
            return IndexStats(added = files.size)
        }

        override suspend fun removeNotSeen(scope: ScanScope, scanId: Long): Int {
            prunes += scope to scanId
            return 7
        }

        override suspend fun filesSeenIn(scanId: Long) = emptyList<IndexedFile>()

        override suspend fun saveHashes(files: List<IndexedFile>) = Unit

        override suspend fun imagesSeenIn(scanId: Long) = emptyList<ImageFile>()
        override suspend fun saveImageHashes(hashes: Map<Long, ImageHashes>) = Unit
        override suspend fun videosSeenIn(scanId: Long) = emptyList<VideoFile>()
        override suspend fun saveVideoSignatures(signatures: Map<Long, VideoSignature>) = Unit
        override suspend fun documentsSeenIn(scanId: Long) = emptyList<DocumentFile>()
        override suspend fun saveDocumentHashes(hashes: Map<Long, Long>) = Unit
        override suspend fun audioSeenIn(scanId: Long) = emptyList<AudioFile>()
        override suspend fun saveAudioSignatures(signatures: Map<Long, AudioSignature>) = Unit
        override suspend fun embeddingCandidates(scanId: Long, modelKey: String, kinds: Set<MediaKind>) = emptyList<EmbeddedFile>()
        override suspend fun saveEmbeddings(modelKey: String, vectors: Map<Long, FloatArray>) = Unit
        override suspend fun deleteEmbeddings(modelId: String) = Unit
        override suspend fun filesByIds(ids: Collection<Long>) = emptyList<StoredFile>()

        override suspend fun remove(ids: Collection<Long>) = Unit
    }

    @Test
    fun `files are recorded in batches and unseen files pruned once at the end`() = runTest {
        val repo = RecordingRepository()
        val files = flow { repeat(1_201) { emit(file(it)) } }

        val stats = IndexFilesUseCase(FakeEnumerator(files), repo)(ScanScope.MediaOnly, filter, scanId = 3)

        assertEquals(listOf(500, 500, 201), repo.batches.map { it.size })
        assertEquals(1_201, repo.batches.flatten().map { it.sourceUri }.toSet().size)
        assertEquals(listOf(ScanScope.MediaOnly to 3L), repo.prunes)
        assertEquals(IndexStats(added = 1_201, removed = 7), stats)
    }

    @Test
    fun `a failed enumeration prunes nothing`() = runTest {
        val repo = RecordingRepository()
        val files = flow {
            repeat(600) { emit(file(it)) }
            throw IOException("volume removed")
        }

        val error = runCatching { IndexFilesUseCase(FakeEnumerator(files), repo)(ScanScope.WholeDevice, filter, 4) }.exceptionOrNull()

        assertTrue(error is IOException)
        assertEquals(listOf(500), repo.batches.map { it.size })
        assertTrue(repo.prunes.isEmpty())
    }

    @Test
    fun `a cancelled scan prunes nothing`() = runTest {
        val repo = RecordingRepository()
        val files = flow {
            repeat(10_000) {
                emit(file(it))
                yield()
            }
        }

        val job = launch { IndexFilesUseCase(FakeEnumerator(files), repo)(ScanScope.WholeDevice, filter, 5) }
        repeat(1_000) { yield() }
        job.cancel()
        job.join()

        assertTrue(repo.batches.isNotEmpty())
        assertTrue(repo.prunes.isEmpty())
    }
}
