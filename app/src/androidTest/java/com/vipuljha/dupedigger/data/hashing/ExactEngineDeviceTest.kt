package com.vipuljha.dupedigger.data.hashing

import android.Manifest
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.data.local.db.DupeDiggerDatabase
import com.vipuljha.dupedigger.data.repository.FileIndexRepositoryImpl
import com.vipuljha.dupedigger.data.storage.FileTreeWalker
import com.vipuljha.dupedigger.data.storage.StorageVolumes
import com.vipuljha.dupedigger.domain.model.ScanFilter
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.repository.FileEnumerator
import com.vipuljha.dupedigger.domain.usecase.FindExactDuplicatesUseCase
import com.vipuljha.dupedigger.domain.usecase.IndexFilesUseCase
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.io.File
import kotlin.random.Random

/** P3-01 on a device: the exact engine over Room and a real folder, and hashing through a `content://` URI. */
@RunWith(AndroidJUnit4::class)
class ExactEngineDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root = File(context.cacheDir, "exact-test")
    private val hasher = StreamingContentHasher(context, Dispatchers.Default)
    private lateinit var db: DupeDiggerDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, DupeDiggerDatabase::class.java).build()
        root.deleteRecursively()
        root.mkdirs()
    }

    @After
    fun tearDown() {
        db.close()
        root.deleteRecursively()
    }

    @Test
    fun findsCopiesThroughTheIndexAndReusesItsHashes() = runBlocking {
        val video = Random(1).nextBytes(3 * 1024 * 1024)
        listOf("a.mp4", "b.mp4", "c.mp4").forEach { File(root, it).writeBytes(video) }
        File(root, "near.mp4").writeBytes(video.copyOf().apply { this[1_000_000] = 0 })
        repeat(200) { File(root, "unique$it.bin").writeBytes(ByteArray(it + 1)) }
        val repository = FileIndexRepositoryImpl(db, db.fileIndexDao(), db.fileHashDao(), db.perceptualHashDao(), db.embeddingDao(), StorageVolumes(context))
        val walker = FileTreeWalker { null }
        val enumerator = object : FileEnumerator {
            override fun enumerate(scope: ScanScope, filter: ScanFilter) = walker.walk(listOf(root), filter)
        }
        IndexFilesUseCase(enumerator, repository)(ScanScope.WholeDevice, ScanFilter(emptyList(), skipHidden = false), scanId = 1)
        val findExact = FindExactDuplicatesUseCase(repository, hasher)

        val first = findExact(scanId = 1, minSizeBytes = 1)
        val second = findExact(scanId = 1, minSizeBytes = 1)

        assertEquals(1, first.groups.size)
        assertEquals(3, first.groups.single().fileIds.size)
        assertEquals(4, first.partialHashed)
        assertEquals(4, first.fullHashed)
        assertEquals(first.groups, second.groups)
        assertEquals(0, second.partialHashed + second.fullHashed)
        val near = repository.indexedFiles(1).single { it.sourceUri.endsWith("near.mp4") }
        assertTrue(near.partialHash != null && near.fullHash != null)
    }

    /**
     * P10-02 on a device: the whole pipeline, one folder scan each, the copy found across them.
     * The scope has to be folder-shaped: a whole-device scan prunes every `file:` row it didn't see, as it should.
     */
    @Test
    fun findsACopyInAFolderADifferentScanIndexed() = runBlocking {
        val photo = Random(2).nextBytes(512 * 1024)
        val dcim = File(root, "dcim").apply { mkdirs() }
        val download = File(root, "download").apply { mkdirs() }
        File(dcim, "IMG_0001.jpg").writeBytes(photo)
        File(dcim, "only-here.jpg").writeBytes(Random(3).nextBytes(256 * 1024))
        File(download, "IMG_0001.jpg").writeBytes(photo)
        val repository = FileIndexRepositoryImpl(db, db.fileIndexDao(), db.fileHashDao(), db.perceptualHashDao(), db.embeddingDao(), StorageVolumes(context))
        val walker = FileTreeWalker { null }
        val filter = ScanFilter(emptyList(), skipHidden = false)
        val indexOf = { folder: File, scanId: Long ->
            val enumerator = object : FileEnumerator {
                override fun enumerate(scope: ScanScope, filter: ScanFilter) = walker.walk(listOf(folder), filter)
            }
            runBlocking { IndexFilesUseCase(enumerator, repository)(ScanScope.Paths(persistentListOf(folder.name)), filter, scanId) }
        }
        val findExact = FindExactDuplicatesUseCase(repository, hasher)

        indexOf(dcim, 1L)
        findExact(scanId = 1, minSizeBytes = 1)
        indexOf(download, 2L)
        val second = findExact(scanId = 2, minSizeBytes = 1)

        val group = second.groups.single()
        assertEquals(
            setOf("${dcim.toURI()}IMG_0001.jpg", "${download.toURI()}IMG_0001.jpg"),
            repository.indexedFiles(2).filter { it.id in group.fileIds }.map { it.sourceUri }.toSet(),
        )
    }

    /** P10-01 on a device: a move through the real walker and Room keeps the hashes. */
    @Test
    fun aMovedFileIsNotHashedAgain() = runBlocking {
        val clip = Random(4).nextBytes(1024 * 1024)
        listOf("a.mp4", "b.mp4").forEach { File(root, it).writeBytes(clip) }
        val repository = FileIndexRepositoryImpl(db, db.fileIndexDao(), db.fileHashDao(), db.perceptualHashDao(), db.embeddingDao(), StorageVolumes(context))
        val walker = FileTreeWalker { null }
        val filter = ScanFilter(emptyList(), skipHidden = false)
        val enumerator = object : FileEnumerator {
            override fun enumerate(scope: ScanScope, filter: ScanFilter) = walker.walk(listOf(root), filter)
        }
        val index = IndexFilesUseCase(enumerator, repository)
        val findExact = FindExactDuplicatesUseCase(repository, hasher)
        index(ScanScope.WholeDevice, filter, scanId = 1)
        findExact(scanId = 1, minSizeBytes = 1)

        File(root, "moved").mkdirs()
        assertTrue(File(root, "a.mp4").renameTo(File(root, "moved/renamed.mp4")))
        index(ScanScope.WholeDevice, filter, scanId = 2)
        val result = findExact(scanId = 2, minSizeBytes = 1)

        assertEquals(0, result.partialHashed + result.fullHashed)
        assertEquals(2, result.groups.single().fileIds.size)
        assertTrue(repository.indexedFiles(2).any { it.sourceUri.endsWith("moved/renamed.mp4") && it.fullHash != null })
    }

    @Test
    fun hashesAMediaStoreFileThroughItsContentUri() = runBlocking {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, permission)
        // A row still being written has no size yet, and hashing one is meant to fail: ask only for finished rows.
        val media = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.SIZE),
            "${MediaStore.Images.Media.SIZE} > 0",
            null,
            null,
        )?.use { c -> if (c.moveToFirst()) ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0)) to c.getLong(1) else null }
        assumeTrue("no readable images on the device", media != null)
        val (uri, size) = media!!

        val partial = hasher.partialHash(uri.toString(), size)
        val full = hasher.fullHash(uri.toString(), size)
        Timber.i("Hashed %s (%d bytes): %s / %s", uri, size, partial, full)

        assertTrue(partial is Outcome.Success)
        assertTrue(full is Outcome.Success)
    }
}
