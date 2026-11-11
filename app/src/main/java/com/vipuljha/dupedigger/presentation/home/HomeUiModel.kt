package com.vipuljha.dupedigger.presentation.home

import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.core.ui.formatBytes
import com.vipuljha.dupedigger.core.ui.formatCount
import com.vipuljha.dupedigger.core.ui.formatDuration
import com.vipuljha.dupedigger.core.ui.formatRelativeTime
import com.vipuljha.dupedigger.domain.model.KindReclaim
import com.vipuljha.dupedigger.domain.model.ScanRunInfo
import com.vipuljha.dupedigger.domain.model.ScanStatus
import com.vipuljha.dupedigger.domain.model.StorageUsage
import com.vipuljha.dupedigger.presentation.common.label
import kotlinx.collections.immutable.toImmutableList

/** Kinds with the most to free come first. */
fun homeUiModel(
    kinds: List<KindReclaim>,
    lastRun: ScanRunInfo?,
    scanning: Boolean,
    storage: StorageUsage,
    nowMillis: Long,
): HomeUiModel {
    val files = kinds.sumOf { it.files }
    val groups = kinds.sumOf { it.groups }
    val reclaimable = kinds.sumOf { it.bytes }
    return HomeUiModel(
        storage = storageUi(storage, reclaimable),
        reclaimTotal = formatBytes(reclaimable),
        reclaimDetail = if (files == 0) null else UiText.res(
            R.string.home_reclaim_detail,
            UiText.plural(R.plurals.home_duplicate_files, files, formatCount(files)),
            UiText.plural(R.plurals.home_groups, groups, formatCount(groups)),
        ),
        kinds = kinds.filter { it.files > 0 }.sortedByDescending { it.bytes }.map { it.toRow() }
            .toImmutableList(),
        lastScan = lastRun?.toUi(nowMillis),
        scanning = scanning,
    )
}

/**
 * The storage card: how full the device is, and what it would look like with the duplicates gone.
 * Null when no volume could be read, so the card is left out rather than showing zeroes.
 */
private fun storageUi(storage: StorageUsage, reclaimableBytes: Long): StorageUi? {
    if (storage.totalBytes <= 0L) return null
    return StorageUi(
        headline = UiText.res(
            R.string.home_storage_used,
            formatBytes(storage.usedBytes),
            formatBytes(storage.totalBytes)
        ),
        freeNow = UiText.res(R.string.home_storage_free, formatBytes(storage.freeBytes)),
        usedFraction = storage.usedFraction(),
        duplicateFraction = storage.fractionOf(reclaimableBytes),
        // Left out when the gain rounds away at the volume's scale, where it would just repeat "free now".
        afterCleanup = reclaimableBytes
            .takeIf { it > 0 && formatBytes(storage.freeBytes + it) != formatBytes(storage.freeBytes) }
            ?.let { UiText.res(R.string.home_storage_after, formatBytes(storage.freeBytes + it)) },
    )
}

private fun KindReclaim.toRow() = KindRowUi(
    kind = kind,
    label = kind.label(),
    size = formatBytes(bytes),
    detail = UiText.plural(R.plurals.home_duplicate_files, files, formatCount(files)),
)

private fun ScanRunInfo.toUi(nowMillis: Long) = LastScanUi(
    time = formatRelativeTime(finishedAt ?: startedAt, nowMillis),
    scope = scope?.label(),
    status = UiText.res(
        when (status) {
            ScanStatus.RUNNING -> R.string.home_scan_status_running
            ScanStatus.COMPLETED -> R.string.home_scan_status_completed
            ScanStatus.CANCELLED -> R.string.home_scan_status_cancelled
            ScanStatus.FAILED -> R.string.home_scan_status_failed
            ScanStatus.INTERRUPTED -> R.string.home_scan_status_interrupted
        },
    ),
    stats = scanStats(),
)

/** "Took 2 min 5 s · 12,345 files scanned"; either half alone when the record lacks the other, null with neither. */
private fun ScanRunInfo.scanStats(): UiText? {
    val took =
        finishedAt?.let { UiText.res(R.string.home_last_scan_took, formatDuration(it - startedAt)) }
    val files = summary?.index?.scanned?.let {
        UiText.plural(
            R.plurals.home_files_scanned,
            it,
            formatCount(it)
        )
    }
    return when {
        took != null && files != null -> UiText.res(R.string.home_last_scan_stats, took, files)
        else -> took ?: files
    }
}
