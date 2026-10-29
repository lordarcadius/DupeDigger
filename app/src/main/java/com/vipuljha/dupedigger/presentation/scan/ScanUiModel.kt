package com.vipuljha.dupedigger.presentation.scan

import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.core.ui.formatBytes
import com.vipuljha.dupedigger.core.ui.formatCount
import com.vipuljha.dupedigger.domain.model.ScanProgress
import com.vipuljha.dupedigger.domain.model.ScanRunInfo
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.ScanStage
import com.vipuljha.dupedigger.domain.model.ScanStatus
import com.vipuljha.dupedigger.domain.model.kind
import com.vipuljha.dupedigger.presentation.common.label

fun ScanScope.toOption() = ScopeOptionUi(
    kind = kind,
    title = kind.label(),
    body = when (this) {
        ScanScope.WholeDevice -> UiText.res(R.string.scan_scope_whole_device_body)
        ScanScope.MediaOnly -> UiText.res(R.string.scan_scope_media_body)
        is ScanScope.Folders -> UiText.plural(R.plurals.scan_scope_folders_body, folders.size, formatCount(folders.size))
    },
)

/** Files per second since the stage began; null until a second has passed, so the first value isn't noise. */
fun filesPerSecond(doneSinceStageStart: Int, elapsedNanos: Long): Int? =
    if (elapsedNanos < NANOS_PER_SECOND) null else (doneSinceStageStart * NANOS_PER_SECOND / elapsedNanos).toInt()

private const val NANOS_PER_SECOND = 1_000_000_000L

fun ScanProgress.toUi(rate: Int?) = ScanProgressUi(
    stage = UiText.res(
        when (stage) {
            ScanStage.INDEXING -> R.string.scan_stage_indexing
            ScanStage.EXACT -> R.string.scan_stage_exact
            ScanStage.NEAR -> R.string.scan_stage_near
            ScanStage.SEMANTIC -> R.string.scan_stage_semantic
        },
    ),
    count = when {
        stage == ScanStage.INDEXING -> UiText.plural(R.plurals.scan_files_found, done, formatCount(done))
        total != null && total > 0 -> UiText.res(R.string.scan_compared, formatCount(done), formatCount(total))
        else -> null
    },
    rate = rate?.let { UiText.plural(R.plurals.scan_rate, it, formatCount(it)) },
    fraction = total?.takeIf { stage != ScanStage.INDEXING && it > 0 }?.let { done.coerceAtMost(it).toFloat() / it },
)

fun ScanRunInfo.toResult(): ScanResultUi = when (status) {
    ScanStatus.COMPLETED -> {
        val found = summary?.duplicateFiles ?: 0
        ScanResultUi(
            title = UiText.res(R.string.scan_done_title),
            body = if (found == 0) {
                UiText.res(R.string.scan_done_none)
            } else {
                UiText.plural(R.plurals.scan_done_found, found, formatCount(found), formatBytes(summary?.reclaimableBytes ?: 0))
            },
            isError = false,
            hasResults = found > 0,
        )
    }
    ScanStatus.CANCELLED, ScanStatus.INTERRUPTED -> ScanResultUi(
        title = UiText.res(R.string.scan_cancelled_title),
        body = UiText.res(R.string.scan_cancelled_body),
        isError = false,
        hasResults = false,
    )
    ScanStatus.FAILED, ScanStatus.RUNNING -> ScanResultUi(
        title = UiText.res(R.string.scan_failed_title),
        body = UiText.res(R.string.scan_failed_body),
        isError = true,
        hasResults = false,
    )
}
