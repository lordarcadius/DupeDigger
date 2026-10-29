package com.vipuljha.dupedigger.data.storage

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import com.vipuljha.dupedigger.core.common.IoDispatcher
import com.vipuljha.dupedigger.data.local.db.dao.QuarantineDao
import com.vipuljha.dupedigger.data.local.db.entity.QuarantineItemEntity
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.RemovalResult
import com.vipuljha.dupedigger.domain.model.StoredFile
import com.vipuljha.dupedigger.domain.model.TrashConsent
import com.vipuljha.dupedigger.domain.repository.FileRemover
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Removes files recoverably (D-007).
 * - Media with a MediaStore row goes to the system trash: directly where the app may, otherwise through one consent prompt.
 * - Everything else is quarantined in the app's files folder: copied, checked against the indexed size,
 *   recorded, and only then deleted at the source; if that delete fails, the copy and record are undone.
 * - A file that can't be moved safely is reported as failed and left where it was.
 */
@Singleton
class AndroidFileRemover @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val quarantineDao: QuarantineDao,
) : FileRemover {

    private val resolver: ContentResolver get() = context.contentResolver

    override suspend fun remove(files: List<StoredFile>): RemovalResult = withContext(ioDispatcher) {
        val removed = mutableListOf<Long>()
        val trashed = mutableListOf<String>()
        val quarantined = mutableListOf<Long>()
        val failed = mutableListOf<Long>()
        val needsConsent = mutableListOf<Pair<Long, Uri>>()
        for (file in files) {
            ensureActive()
            val mediaUri = if (file.mediaKind in MEDIA_KINDS) resolver.mediaCollectionUri(file.sourceUri, file.path) else null
            if (mediaUri != null) {
                if (trashDirectly(mediaUri)) {
                    removed += file.id
                    trashed += mediaUri.toString()
                } else {
                    needsConsent += file.id to mediaUri
                }
            } else {
                val quarantineId = quarantine(file)
                if (quarantineId != null) {
                    removed += file.id
                    quarantined += quarantineId
                } else {
                    failed += file.id
                }
            }
        }
        RemovalResult(
            removedIds = removed.toImmutableList(),
            trashedUris = trashed.toImmutableList(),
            quarantineIds = quarantined.toImmutableList(),
            consent = needsConsent.takeIf { it.isNotEmpty() }?.let { pending ->
                TrashConsent(
                    fileIds = pending.map { it.first }.toImmutableList(),
                    mediaUris = pending.map { it.second.toString() }.toImmutableList(),
                    platformRequest = MediaStore.createTrashRequest(resolver, pending.map { it.second }, true).intentSender,
                )
            },
            failedIds = failed.toImmutableList(),
        )
    }

    /** Works for media the app owns, and where the platform lets it trash others' media without asking. */
    private fun trashDirectly(uri: Uri): Boolean = try {
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_TRASHED, 1) }, null, null) > 0
    } catch (e: SecurityException) {
        false
    }

    private suspend fun quarantine(file: StoredFile): Long? {
        val copy = File(quarantineDir(), "${UUID.randomUUID()}-${file.name}")
        if (!copyChecked(file, copy)) return null
        val id = quarantineDao.insert(
            QuarantineItemEntity(
                sourceUri = file.sourceUri,
                originalPath = file.path ?: file.sourceUri,
                quarantinePath = copy.path,
                size = file.size,
                movedAt = System.currentTimeMillis(),
            ),
        )
        if (deleteSource(file.sourceUri)) return id
        quarantineDao.delete(id)
        copy.delete()
        return null
    }

    private fun quarantineDir(): File = File(context.getExternalFilesDir(null) ?: context.filesDir, QUARANTINE_DIR).apply { mkdirs() }

    private fun copyChecked(file: StoredFile, copy: File): Boolean {
        val ok = try {
            copy.parentFile!!.usableSpace > file.size + FREE_SPACE_MARGIN &&
                resolver.openSource(file.sourceUri).use { input -> FileOutputStream(copy).use { input.copyTo(it, BUFFER) } } == file.size
        } catch (e: IOException) {
            Timber.w(e, "Couldn't copy a file into quarantine")
            false
        } catch (e: SecurityException) {
            Timber.w(e, "Couldn't copy a file into quarantine")
            false
        }
        if (!ok) copy.delete()
        return ok
    }

    private fun deleteSource(sourceUri: String): Boolean = try {
        if (sourceUri.startsWith("file:")) {
            File(URI(sourceUri)).delete()
        } else {
            DocumentsContract.deleteDocument(resolver, Uri.parse(sourceUri))
        }
    } catch (e: IOException) {
        false
    } catch (e: SecurityException) {
        false
    } catch (e: IllegalArgumentException) {
        false
    }

    private companion object {
        val MEDIA_KINDS = setOf(MediaKind.IMAGE, MediaKind.VIDEO, MediaKind.AUDIO)
        const val QUARANTINE_DIR = "quarantine"
        const val BUFFER = 256 * 1024
        const val FREE_SPACE_MARGIN = 64L * 1024 * 1024
    }
}
