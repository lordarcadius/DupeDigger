package com.vipuljha.dupedigger.data.repository

import androidx.room.withTransaction
import com.vipuljha.dupedigger.data.local.db.DupeDiggerDatabase
import com.vipuljha.dupedigger.data.local.db.dao.ScanRunDao
import com.vipuljha.dupedigger.data.local.db.entity.ScanRunEntity
import com.vipuljha.dupedigger.data.worker.ScanScopeCodec
import com.vipuljha.dupedigger.domain.model.IndexStats
import com.vipuljha.dupedigger.domain.model.ScanRunInfo
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.ScanStatus
import com.vipuljha.dupedigger.domain.model.ScanSummary
import com.vipuljha.dupedigger.domain.repository.ScanRunRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScanRunRepositoryImpl @Inject constructor(
    private val db: DupeDiggerDatabase,
    private val dao: ScanRunDao,
) : ScanRunRepository {

    override suspend fun start(scope: ScanScope): Long = db.withTransaction {
        val now = System.currentTimeMillis()
        dao.closeRunning(ScanStatus.INTERRUPTED.name, now, running = ScanStatus.RUNNING.name)
        dao.insert(
            ScanRunEntity(
                scope = ScanScopeCodec.key(scope),
                tiers = TIERS,
                startedAt = now,
                finishedAt = null,
                status = ScanStatus.RUNNING.name,
                stats = "{}",
            ),
        )
    }

    override suspend fun finish(scanId: Long, status: ScanStatus, summary: ScanSummary?) {
        val stats = summary?.let { Json.encodeToString(ScanRunStats.serializer(), it.toStats()) } ?: "{}"
        dao.finish(scanId, status.name, System.currentTimeMillis(), stats)
    }

    override fun observeLatest(): Flow<ScanRunInfo?> = dao.observeLatest().map { it?.toInfo() }

    private fun ScanRunEntity.toInfo() = ScanRunInfo(
        id = id,
        scope = ScanScopeCodec.kindOf(scope),
        status = ScanStatus.entries.firstOrNull { it.name == status } ?: ScanStatus.FAILED,
        startedAt = startedAt,
        finishedAt = finishedAt,
        summary = if (status == ScanStatus.COMPLETED.name) decodeStats(stats)?.toSummary() else null,
    )

    // Stats written by an older build may not decode; the run then shows without numbers.
    private fun decodeStats(json: String): ScanRunStats? = try {
        Json.decodeFromString(ScanRunStats.serializer(), json)
    } catch (e: SerializationException) {
        null
    } catch (e: IllegalArgumentException) {
        null
    }

    private fun ScanRunStats.toSummary() = ScanSummary(
        index = IndexStats(added = filesAdded, changed = filesChanged, unchanged = filesUnchanged, removed = filesRemoved),
        exactGroups = exactGroups,
        duplicateFiles = duplicateFiles,
        reclaimableBytes = reclaimableBytes,
        hashesComputed = hashesComputed,
        unreadable = unreadable,
    )

    private fun ScanSummary.toStats() = ScanRunStats(
        filesAdded = index.added,
        filesChanged = index.changed,
        filesUnchanged = index.unchanged,
        filesRemoved = index.removed,
        exactGroups = exactGroups,
        duplicateFiles = duplicateFiles,
        reclaimableBytes = reclaimableBytes,
        hashesComputed = hashesComputed,
        unreadable = unreadable,
    )

    private companion object {
        const val TIERS = "EXACT"
    }
}

/** The `scan_run.stats` JSON. */
@Serializable
data class ScanRunStats(
    val filesAdded: Int,
    val filesChanged: Int,
    val filesUnchanged: Int,
    val filesRemoved: Int,
    val exactGroups: Int,
    val duplicateFiles: Int,
    val reclaimableBytes: Long,
    val hashesComputed: Int,
    val unreadable: Int,
)
