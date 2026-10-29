package com.vipuljha.dupedigger.data.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.common.IoDispatcher
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.core.common.runSuspendCatching
import com.vipuljha.dupedigger.domain.model.ScanFolder
import com.vipuljha.dupedigger.domain.repository.ScanFolderRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** The system's persisted tree grants are the list of picked folders, so nothing else has to be stored. */
@Singleton
class AndroidScanFolderRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ScanFolderRepository {

    private val resolver get() = context.contentResolver
    private val changes = MutableStateFlow(0)

    override val folders: Flow<ImmutableList<ScanFolder>> = changes
        .map { readFolders() }
        .flowOn(ioDispatcher)

    override suspend fun add(treeUri: String): Outcome<Unit> = runSuspendCatching {
        withContext(ioDispatcher) { persist(Uri.parse(treeUri)) }
    }.fold(
        onSuccess = {
            changes.update { it + 1 }
            Outcome.Success(Unit)
        },
        onFailure = { Outcome.Failure(DomainError.PermissionDenied, it) },
    )

    override suspend fun remove(treeUri: String) {
        withContext(ioDispatcher) {
            runSuspendCatching { resolver.releasePersistableUriPermission(Uri.parse(treeUri), READ_WRITE) }
                .recoverCatching { resolver.releasePersistableUriPermission(Uri.parse(treeUri), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                .onFailure { Timber.w(it, "Couldn't release a folder grant") }
        }
        changes.update { it + 1 }
    }

    // Write is kept when granted, so non-media duplicates in a picked folder can be quarantined later.
    private fun persist(uri: Uri) {
        try {
            resolver.takePersistableUriPermission(uri, READ_WRITE)
        } catch (_: SecurityException) {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun readFolders(): ImmutableList<ScanFolder> = resolver.persistedUriPermissions
        .filter { it.isReadPermission && DocumentsContract.isTreeUri(it.uri) }
        .map { it.uri.toScanFolder() }
        .sortedBy { it.name.lowercase() }
        .toImmutableList()

    private fun Uri.toScanFolder(): ScanFolder {
        val docId = DocumentsContract.getTreeDocumentId(this)
        return ScanFolder(
            uri = toString(),
            name = displayName(docId) ?: docId.substringAfterLast(':').substringAfterLast('/').ifEmpty { docId },
            path = if (authority == EXTERNAL_STORAGE_AUTHORITY) externalStorageDocIdToPath(docId) else null,
        )
    }

    private fun Uri.displayName(docId: String): String? {
        val document = DocumentsContract.buildDocumentUriUsingTree(this, docId)
        return runCatching {
            resolver.query(document, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
    }

    private companion object {
        const val READ_WRITE = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
}
