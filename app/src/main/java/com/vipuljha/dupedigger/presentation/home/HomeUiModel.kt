package com.vipuljha.dupedigger.presentation.home

import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.core.ui.formatBytes
import com.vipuljha.dupedigger.core.ui.formatCount
import com.vipuljha.dupedigger.core.ui.formatRelativeTime
import com.vipuljha.dupedigger.domain.model.KindReclaim
import com.vipuljha.dupedigger.domain.model.ScanRunInfo
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.ScanStatus
import com.vipuljha.dupedigger.presentation.common.label
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** Kinds with the most to free come first. */
fun homeUiModel(kinds: List<KindReclaim>, lastRun: ScanRunInfo?, scanning: Boolean, nowMillis: Long): HomeUiModel {
    val files = kinds.sumOf { it.files }
    val groups = kinds.sumOf { it.groups }
    return HomeUiModel(
        reclaimTotal = formatBytes(kinds.sumOf { it.bytes }),
        reclaimDetail = if (files == 0) null else UiText.res(
            R.string.home_reclaim_detail,
            UiText.plural(R.plurals.home_duplicate_files, files, formatCount(files)),
            UiText.plural(R.plurals.home_groups, groups, formatCount(groups)),
        ),
        kinds = kinds.filter { it.files > 0 }.sortedByDescending { it.bytes }.map { it.toRow() }.toImmutableList(),
        lastScan = lastRun?.toUi(nowMillis),
        scanning = scanning,
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
)

/** Everything and Media need their access; picking a folder always works. */
fun quickActions(available: List<ScanScope>): ImmutableList<QuickActionUi> = listOf(
    QuickActionUi(
        QuickAction.Everything,
        enabled = ScanScope.WholeDevice in available,
        hint = if (ScanScope.WholeDevice in available) null else UiText.res(R.string.home_hint_all_files),
    ),
    QuickActionUi(
        QuickAction.Media,
        enabled = ScanScope.MediaOnly in available,
        hint = if (ScanScope.MediaOnly in available) null else UiText.res(R.string.home_hint_media),
    ),
    QuickActionUi(QuickAction.Folder, enabled = true, hint = null),
).toImmutableList()
