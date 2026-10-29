package com.vipuljha.dupedigger.data.storage

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.vipuljha.dupedigger.core.common.IoDispatcher
import com.vipuljha.dupedigger.domain.model.DiscoveredFile
import com.vipuljha.dupedigger.domain.model.ScanFilter
import com.vipuljha.dupedigger.domain.model.ScanFolder
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.mediaKindOf
import com.vipuljha.dupedigger.domain.repository.FileEnumerator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidFileEnumerator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : FileEnumerator {

    private val resolver: ContentResolver get() = context.contentResolver
    private val walker = FileTreeWalker { name ->
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase())
    }

    override fun enumerate(scope: ScanScope, filter: ScanFilter): Flow<DiscoveredFile> = when (scope) {
        ScanScope.WholeDevice -> walker.walk(volumeRoots(), filter)
        ScanScope.MediaOnly -> mediaStore(filter)
        is ScanScope.Folders -> flow { scope.folders.forEach { emitAll(tree(it, filter)) } }
    }.flowOn(ioDispatcher)

    private fun volumeRoots(): List<File> =
        context.getSystemService(StorageManager::class.java).storageVolumes.mapNotNull { it.directory }

    /** Images, videos and audio on every external volume; the system already hides trashed and pending rows. */
    private fun mediaStore(filter: ScanFilter): Flow<DiscoveredFile> = flow {
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.VOLUME_NAME,
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?, ?)"
        val args = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE,
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO,
            MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO,
        ).map(Int::toString).toTypedArray()
        val uri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        resolver.query(uri, projection, selection, args, null)?.use { c ->
            while (c.moveToNext()) {
                currentCoroutineContext().ensureActive()
                val path = c.getString(1)
                val name = c.getString(2) ?: path?.substringAfterLast('/') ?: continue
                if (filter.skips(path, name)) continue
                val volume = c.getString(6) ?: MediaStore.VOLUME_EXTERNAL_PRIMARY
                val mime = c.getString(3)
                emit(
                    DiscoveredFile(
                        sourceUri = ContentUris.withAppendedId(MediaStore.Files.getContentUri(volume), c.getLong(0)).toString(),
                        path = path,
                        name = name,
                        mimeType = mime,
                        mediaKind = mediaKindOf(mime),
                        size = c.getLong(4),
                        mtime = c.getLong(5) * 1000,
                        volume = volume,
                    ),
                )
            }
        }
    }

    /** Breadth-first walk of a picked SAF tree with one children query per folder. */
    private fun tree(folder: ScanFolder, filter: ScanFilter): Flow<DiscoveredFile> = flow {
        val treeUri = Uri.parse(folder.uri)
        val onLocalStorage = treeUri.authority == EXTERNAL_STORAGE_AUTHORITY
        val pending = ArrayDeque<String>().apply { addLast(DocumentsContract.getTreeDocumentId(treeUri)) }
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, pending.removeFirst())
            resolver.query(children, projection, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val docId = c.getString(0)
                    val name = c.getString(1) ?: docId.substringAfterLast('/')
                    val path = if (onLocalStorage) externalStorageDocIdToPath(docId) else null
                    if (filter.skips(path, name)) continue
                    val mime = c.getString(2)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        pending.addLast(docId)
                        continue
                    }
                    emit(
                        DiscoveredFile(
                            sourceUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId).toString(),
                            path = path,
                            name = name,
                            mimeType = mime,
                            mediaKind = mediaKindOf(mime),
                            size = if (c.isNull(3)) 0L else c.getLong(3),
                            mtime = if (c.isNull(4)) 0L else c.getLong(4),
                            volume = treeUri.authority,
                        ),
                    )
                }
            }
        }
    }
}
