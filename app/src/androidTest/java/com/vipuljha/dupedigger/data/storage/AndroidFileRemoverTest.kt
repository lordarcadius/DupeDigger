package com.vipuljha.dupedigger.data.storage

import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vipuljha.dupedigger.data.local.db.DupeDiggerDatabase
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.StoredFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** P3-04 on a device: media goes to the system trash and can be restored; other files are quarantined safely. */
@RunWith(AndroidJUnit4::class)
class AndroidFileRemoverTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver = context.contentResolver
    private lateinit var db: DupeDiggerDatabase
    private lateinit var remover: AndroidFileRemover
    private val inserted = mutableListOf<Uri>()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, DupeDiggerDatabase::class.java).build()
        remover = AndroidFileRemover(context, Dispatchers.IO, db.quarantineDao())
    }

    @After
    fun tearDown() {
        inserted.forEach { resolver.delete(it, includeTrashed()) }
        db.close()
    }

    private fun includeTrashed() = Bundle().apply { putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_INCLUDE) }

    private class TestImage(val filesUri: Uri, val imageUri: Uri, val path: String, val size: Long) {
        val id get() = ContentUris.parseId(imageUri)
    }

    /** An image this app owns; the MediaStore scan addresses it by its Files-collection URI. */
    private fun insertImage(name: String): TestImage {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/DupeDiggerTest")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val imageUri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)!!
        resolver.openOutputStream(imageUri)!!.use { it.write(ByteArray(4096) { i -> i.toByte() }) }
        resolver.update(imageUri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        val filesUri = ContentUris.withAppendedId(MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), ContentUris.parseId(imageUri))
        inserted += filesUri
        val (path, size) = resolver.query(filesUri, arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.SIZE), null, null)!!
            .use { it.moveToFirst(); it.getString(0) to it.getLong(1) }
        return TestImage(filesUri, imageUri, path, size)
    }

    /** Whether a collection query, as the MediaStore scan makes, still returns the item. */
    private fun isVisible(image: TestImage) = resolver.query(
        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
        arrayOf(MediaStore.MediaColumns._ID),
        "${MediaStore.MediaColumns._ID} = ?",
        arrayOf(image.id.toString()),
        null,
    )!!.use { it.count == 1 }

    private fun isTrashed(uri: Uri) = resolver.query(uri, arrayOf(MediaStore.MediaColumns.IS_TRASHED), includeTrashed(), null)!!
        .use { it.moveToFirst() && it.getInt(0) == 1 }

    private fun restore(uri: Uri) = resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 0) }, includeTrashed())

    @Test
    fun mediaGoesToTheSystemTrashAndCanBeRestored() = runBlocking<Unit> {
        val image = insertImage("trash-${System.nanoTime()}.jpg")
        assertTrue(isVisible(image))

        val result = remover.remove(listOf(StoredFile(1, image.filesUri.toString(), image.path, "a.jpg", MediaKind.IMAGE, image.size)))

        assertEquals(listOf(1L), result.removedIds)
        assertEquals(listOf(image.imageUri.toString()), result.trashedUris)
        assertNull(result.consent)
        assertFalse(isVisible(image))
        assertTrue(isTrashed(image.imageUri))
        assertEquals(0, remover.untrash(result.trashedUris))
        assertTrue(isVisible(image))
    }

    @Test
    fun aWholeDeviceFileIsFoundInMediaStoreByItsPath() = runBlocking<Unit> {
        val image = insertImage("path-${System.nanoTime()}.jpg")

        val result = remover.remove(listOf(StoredFile(2, File(image.path).toURI().toString(), image.path, "b.jpg", MediaKind.IMAGE, image.size)))

        assertEquals(listOf(image.imageUri.toString()), result.trashedUris)
        assertFalse(isVisible(image))
        assertEquals(1, restore(image.imageUri))
    }

    @Test
    fun theTrashPromptTakesTypedMediaUrisButNotFilesUris() {
        val image = insertImage("consent-${System.nanoTime()}.jpg")
        assertNotNull(MediaStore.createTrashRequest(resolver, listOf(image.imageUri), true).intentSender)
        assertTrue(runCatching { MediaStore.createTrashRequest(resolver, listOf(image.filesUri), true) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun otherFilesAreQuarantinedWithARecordAndTheSourceGone() = runBlocking<Unit> {
        val bytes = "meeting notes".toByteArray()
        val source = File(context.cacheDir, "notes-${System.nanoTime()}.txt").apply { writeBytes(bytes) }
        val sourceUri = source.toURI().toString()

        val result = remover.remove(listOf(StoredFile(3, sourceUri, source.path, source.name, MediaKind.DOCUMENT, bytes.size.toLong())))

        assertEquals(listOf(3L), result.removedIds)
        assertFalse(source.exists())
        val row = db.quarantineDao().get(result.quarantineIds.single())!!
        assertEquals(sourceUri, row.sourceUri)
        assertEquals(source.path, row.originalPath)
        val copy = File(row.quarantinePath)
        assertArrayEquals(bytes, copy.readBytes())
        copy.delete()
    }

    @Test
    fun aFileThatChangedSinceIndexingIsLeftAlone() = runBlocking<Unit> {
        val source = File(context.cacheDir, "changed-${System.nanoTime()}.txt").apply { writeBytes(ByteArray(10)) }

        val result = remover.remove(listOf(StoredFile(4, source.toURI().toString(), source.path, source.name, MediaKind.OTHER, 99)))

        assertEquals(listOf(4L), result.failedIds)
        assertTrue(source.exists())
        assertTrue(db.quarantineDao().observeAll().first().isEmpty())
        source.delete()
    }
}
