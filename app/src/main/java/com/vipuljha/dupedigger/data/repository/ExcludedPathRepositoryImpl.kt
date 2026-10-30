package com.vipuljha.dupedigger.data.repository

import android.net.Uri
import android.provider.DocumentsContract
import com.vipuljha.dupedigger.core.common.DomainError
import com.vipuljha.dupedigger.core.common.Outcome
import com.vipuljha.dupedigger.data.local.db.dao.ExcludedPathDao
import com.vipuljha.dupedigger.data.local.db.entity.ExcludedPathEntity
import com.vipuljha.dupedigger.data.storage.EXTERNAL_STORAGE_AUTHORITY
import com.vipuljha.dupedigger.data.storage.externalStorageDocIdToPath
import com.vipuljha.dupedigger.domain.repository.ExcludedPathRepository
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExcludedPathRepositoryImpl @Inject constructor(
    private val dao: ExcludedPathDao,
) : ExcludedPathRepository {

    override val paths: Flow<ImmutableList<String>> =
        dao.observeAll().map { rows -> rows.map { it.path }.toImmutableList() }

    override suspend fun excludeTree(treeUri: String): Outcome<Unit> {
        val uri = Uri.parse(treeUri)
        val path = if (uri.authority == EXTERNAL_STORAGE_AUTHORITY && DocumentsContract.isTreeUri(uri)) {
            externalStorageDocIdToPath(DocumentsContract.getTreeDocumentId(uri))
        } else {
            null
        } ?: return Outcome.Failure(DomainError.NotFound)
        dao.upsert(ExcludedPathEntity(path, addedAt = System.currentTimeMillis()))
        return Outcome.Success(Unit)
    }

    override suspend fun remove(path: String) = dao.delete(path)
}
