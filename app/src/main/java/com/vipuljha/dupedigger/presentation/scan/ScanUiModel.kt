package com.vipuljha.dupedigger.presentation.scan

import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.core.ui.formatBytes
import com.vipuljha.dupedigger.core.ui.formatCount
import com.vipuljha.dupedigger.domain.model.AllMediaKinds
import com.vipuljha.dupedigger.domain.model.BuiltInPreset
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.ScanFolder
import com.vipuljha.dupedigger.domain.model.ScanPreset
import com.vipuljha.dupedigger.domain.model.ScanProgress
import com.vipuljha.dupedigger.domain.model.ScanRunInfo
import com.vipuljha.dupedigger.domain.model.ScanScope
import com.vipuljha.dupedigger.domain.model.ScanStage
import com.vipuljha.dupedigger.domain.model.ScanStatus
import com.vipuljha.dupedigger.domain.model.ScopeKind
import com.vipuljha.dupedigger.domain.model.isReachableWith
import com.vipuljha.dupedigger.domain.model.kind
import com.vipuljha.dupedigger.domain.model.roots
import com.vipuljha.dupedigger.presentation.common.label
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/** A built-in preset is named by its app; a disabled preset says what it needs. */
fun ScanPreset.toUi(available: List<ScanScope>): PresetUi {
    val reachable = scope.isReachableWith(available)
    return PresetUi(
        id = id,
        title = builtIn?.let { UiText.res(it.titleRes()) } ?: UiText.Raw(name),
        body = UiText.res(R.string.scan_preset_detail, scope.summary(), kindsSummary(kinds)),
        enabled = reachable,
        hint = if (reachable) null else when (scope) {
            ScanScope.MediaOnly -> UiText.res(R.string.home_hint_media)
            is ScanScope.Folders -> UiText.res(R.string.scan_preset_folders_gone)
            ScanScope.WholeDevice, is ScanScope.Paths -> UiText.res(R.string.home_hint_all_files)
        },
        editable = deletable,
    )
}

private fun BuiltInPreset.titleRes() = when (this) {
    BuiltInPreset.WHATSAPP -> R.string.preset_whatsapp
    BuiltInPreset.CAMERA -> R.string.preset_camera
    BuiltInPreset.TELEGRAM -> R.string.preset_telegram
    BuiltInPreset.SCREENSHOTS -> R.string.preset_screenshots
    BuiltInPreset.FACEBOOK -> R.string.preset_facebook
    BuiltInPreset.INSTAGRAM -> R.string.preset_instagram
    BuiltInPreset.SNAPCHAT -> R.string.preset_snapchat
    BuiltInPreset.REDDIT -> R.string.preset_reddit
}

private fun ScanScope.summary(): UiText = when (this) {
    ScanScope.WholeDevice -> UiText.res(R.string.scope_whole_device)
    ScanScope.MediaOnly -> UiText.res(R.string.scope_media)
    is ScanScope.Folders -> UiText.plural(
        R.plurals.scan_scope_folders_body,
        folders.size,
        formatCount(folders.size)
    )

    is ScanScope.Paths -> roots().size.let {
        UiText.plural(
            R.plurals.scan_scope_paths_body,
            it,
            formatCount(it)
        )
    }
}

/** "All file types", one type's name, or how many types. */
fun kindsSummary(kinds: Set<MediaKind>): UiText = when {
    kinds.containsAll(AllMediaKinds) -> UiText.res(R.string.scan_kinds_all)
    kinds.size == 1 -> kinds.single().label()
    else -> UiText.plural(R.plurals.scan_kinds_count, kinds.size, formatCount(kinds.size))
}

fun kindOptions(selected: Set<MediaKind>): ImmutableList<KindOptionUi> =
    MediaKind.entries.map { KindOptionUi(it, it.label(), it in selected) }.toImmutableList()

/** Whole device and media library when they can be read, then picked folders, which can always be added to. */
fun whereOptions(
    available: List<ScanScope>,
    picked: List<ScanFolder>
): ImmutableList<ScopeOptionUi> =
    (available.filterNot { it is ScanScope.Folders }.map { it.toOption() } + ScopeOptionUi(
        kind = ScopeKind.FOLDERS,
        title = ScopeKind.FOLDERS.label(),
        body = if (picked.isEmpty()) {
            UiText.res(R.string.scan_scope_folders_none)
        } else {
            UiText.plural(R.plurals.scan_scope_folders_body, picked.size, formatCount(picked.size))
        },
    )).toImmutableList()

fun ScanScope.toOption() = ScopeOptionUi(
    kind = kind,
    title = kind.label(),
    body = when (this) {
        ScanScope.WholeDevice -> UiText.res(R.string.scan_scope_whole_device_body)
        ScanScope.MediaOnly -> UiText.res(R.string.scan_scope_media_body)
        is ScanScope.Folders -> UiText.plural(
            R.plurals.scan_scope_folders_body,
            folders.size,
            formatCount(folders.size)
        )

        is ScanScope.Paths -> roots().size.let {
            UiText.plural(
                R.plurals.scan_scope_paths_body,
                it,
                formatCount(it)
            )
        }
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
        stage == ScanStage.INDEXING -> UiText.plural(
            R.plurals.scan_files_found,
            done,
            formatCount(done)
        )

        total != null && total > 0 -> UiText.res(
            R.string.scan_compared,
            formatCount(done),
            formatCount(total)
        )

        else -> null
    },
    rate = rate?.let { UiText.plural(R.plurals.scan_rate, it, formatCount(it)) },
    fraction = total?.takeIf { stage != ScanStage.INDEXING && it > 0 }
        ?.let { done.coerceAtMost(it).toFloat() / it },
    aiStage = stage == ScanStage.SEMANTIC,
)

fun ScanRunInfo.toResult(): ScanResultUi = when (status) {
    ScanStatus.COMPLETED -> {
        val found = summary?.duplicateFiles ?: 0
        ScanResultUi(
            title = UiText.res(R.string.scan_done_title),
            body = if (found == 0) {
                UiText.res(R.string.scan_done_none)
            } else {
                UiText.plural(
                    R.plurals.scan_done_found,
                    found,
                    formatCount(found),
                    formatBytes(summary?.reclaimableBytes ?: 0)
                )
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
