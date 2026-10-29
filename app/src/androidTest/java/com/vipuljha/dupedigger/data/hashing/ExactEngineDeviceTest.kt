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
import com.vipuljha.dupedigger.domain.model.ScanFilter
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.repository.FileEnumerator
import com.vipuljha.dupedigger.domain.usecase.FindExactDuplicatesUseCase
import com.vipuljha.dupedigger.domain.usecase.IndexFilesUseCase
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
        val repository = FileIndexRepositoryImpl(db, db.fileIndexDao(), db.fileHashDao(), db.perceptualHashDao(), db.embeddingDao())
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
        val near = repository.filesSeenIn(1).single { it.sourceUri.endsWith("near.mp4") }
        assertTrue(near.partialHash != null && near.fullHash != null)
    }

    @Test
    fun hashesAMediaStoreFileThroughItsContentUri() = runBlocking {
        val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(context.packageName, permission)
        val media = context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.SIZE),
            null, null, null,
        )?.use { c -> if (c.moveToFirst()) ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, c.getLong(0)) to c.getLong(1) else null }
        assumeTrue("no images on the device", media != null)
        val (uri, size) = media!!

        val partial = hasher.partialHash(uri.toString(), size)
        val full = hasher.fullHash(uri.toString(), size)
        Timber.i("Hashed %s (%d bytes): %s / %s", uri, size, partial, full)

        assertTrue(partial is Outcome.Success)
        assertTrue(full is Outcome.Success)
    }
}
