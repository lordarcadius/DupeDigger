package com.vipuljha.dupedigger.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.common.IoDispatcher
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.data.local.db.dao.QuarantineDao
import com.vipuljha.dupedigger.data.local.db.entity.QuarantineItemEntity
import com.vipuljha.dupedigger.data.storage.EXTERNAL_STORAGE_AUTHORITY
import com.vipuljha.dupedigger.domain.model.QuarantinedFile
import com.vipuljha.dupedigger.domain.repository.QuarantineRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Restore copies the file back, checks its size, and only then drops the quarantine copy and record.
 * - A `file:` source goes back to its path; nothing is overwritten.
 * - A picked-folder document is recreated in its parent folder through the tree grant (local storage trees only).
 */
@Singleton
class QuarantineRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    private val dao: QuarantineDao,
) : QuarantineRepository {

    private val resolver: ContentResolver get() = context.contentResolver

    override fun observeAll(): Flow<List<QuarantinedFile>> = dao.observeAll().map { rows ->
        rows.map { QuarantinedFile(it.id, it.originalPath.substringAfterLast('/').substringAfterLast(':'), it.originalPath, it.size, it.movedAt) }
    }

    override suspend fun restore(id: Long): Outcome<Unit> {
        val item = dao.get(id) ?: return Outcome.Failure(DomainError.NotFound)
        val restored = withContext(ioDispatcher) { putBack(item) }
        if (restored != null) return Outcome.Failure(restored)
        withContext(ioDispatcher) { File(item.quarantinePath).delete() }
        dao.delete(id)
        return Outcome.Success(Unit)
    }

    override suspend fun deletePermanently(id: Long): Outcome<Unit> {
        val item = dao.get(id) ?: return Outcome.Failure(DomainError.NotFound)
        val copy = File(item.quarantinePath)
        val deleted = withContext(ioDispatcher) { !copy.exists() || copy.delete() }
        if (!deleted) return Outcome.Failure(DomainError.Io)
        dao.delete(id)
        return Outcome.Success(Unit)
    }

    /** Null on success, else why it failed; a partial copy is removed. */
    private fun putBack(item: QuarantineItemEntity): DomainError? = try {
        val copy = File(item.quarantinePath)
        if (!copy.exists()) {
            DomainError.NotFound
        } else if (item.sourceUri.startsWith("file:")) {
            val target = File(URI(item.sourceUri))
            if (target.exists()) {
                DomainError.Io
            } else {
                target.parentFile?.mkdirs()
                val written = try {
                    FileInputStream(copy).use { input -> target.outputStream().use { input.copyTo(it) } }
                } catch (e: IOException) {
                    target.delete()
                    throw e
                }
                if (written == copy.length()) null else DomainError.Io.also { target.delete() }
            }
        } else {
            restoreDocument(item, copy)
        }
    } catch (e: IOException) {
        Timber.w(e, "Couldn't restore a quarantined file")
        DomainError.Io
    } catch (e: SecurityException) {
        Timber.w(e, "Couldn't restore a quarantined file")
        DomainError.PermissionDenied
    }

    private fun restoreDocument(item: QuarantineItemEntity, copy: File): DomainError? {
        val source = Uri.parse(item.sourceUri)
        if (source.authority != EXTERNAL_STORAGE_AUTHORITY || !DocumentsContract.isTreeUri(source)) return DomainError.Io
        val docId = DocumentsContract.getDocumentId(source)
        val parent = DocumentsContract.buildDocumentUriUsingTree(source, docId.substringBeforeLast('/'))
        val name = docId.substringAfterLast('/')
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"
        val created = DocumentsContract.createDocument(resolver, parent, mime, name) ?: return DomainError.Io
        // The provider renames on a clash ("notes (1).txt"); that means the original place is taken.
        val written = try {
            if (DocumentsContract.getDocumentId(created) != docId) {
                null
            } else {
                resolver.openOutputStream(created)?.use { out -> FileInputStream(copy).use { it.copyTo(out) } }
            }
        } catch (e: IOException) {
            DocumentsContract.deleteDocument(resolver, created)
            throw e
        }
        if (written == copy.length()) return null
        DocumentsContract.deleteDocument(resolver, created)
        return DomainError.Io
    }
}
