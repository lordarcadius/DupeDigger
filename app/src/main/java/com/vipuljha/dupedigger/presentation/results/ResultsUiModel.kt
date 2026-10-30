package com.vipuljha.dupedigger.presentation.results

import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.core.ui.formatBytes
import com.vipuljha.dupedigger.core.ui.formatCount
import com.vipuljha.dupedigger.domain.model.DuplicateGroupItem
import com.vipuljha.dupedigger.domain.model.KindReclaim
import com.vipuljha.dupedigger.domain.model.MatchTier
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.presentation.common.label
import com.vipuljha.dupedigger.presentation.common.tierTag
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

fun DuplicateGroupItem.toRow(): GroupRowUi {
    val files = removable.size + 1
    val exact = tier == MatchTier.EXACT
    return GroupRowUi(
        groupId = groupId,
        fingerprint = fingerprint,
        kind = kind,
        title = keeper.name.ifEmpty { keeper.sourceUri.substringAfterLast('/') },
        detail = if (exact) {
            UiText.res(
                R.string.results_row_detail,
                UiText.plural(R.plurals.results_copies, files, formatCount(files), formatBytes(keeper.size)),
                tier.label(),
            )
        } else {
            UiText.plural(R.plurals.results_similar_files, files, formatCount(files))
        },
        reclaim = UiText.res(R.string.results_frees, formatBytes(reclaimableBytes)),
        tierTag = if (exact) null else tierTag(tier, score),
        removable = removable.associate { it.fileId to it.size }.toImmutableMap(),
    )
}

/** "All" then each tier that has groups, in a fixed order, each with its group count. */
fun tierChips(counts: Map<MatchTier, Int>): ImmutableList<TierChipUi> {
    val all = TierChipUi(null, UiText.res(R.string.results_chip_count, UiText.res(R.string.results_chip_all), formatCount(counts.values.sum())))
    return (listOf(all) + MatchTier.entries.mapNotNull { tier ->
        counts[tier]?.takeIf { it > 0 }?.let { TierChipUi(tier, UiText.res(R.string.results_chip_count, tier.label(), formatCount(it))) }
    }).toImmutableList()
}

/** "All" then each kind that has groups, in a fixed order, each with its group count. */
fun kindChips(kinds: List<KindReclaim>): ImmutableList<KindChipUi> {
    val counts = kinds.associate { it.kind to it.groups }
    val all = KindChipUi(null, UiText.res(R.string.results_chip_count, UiText.res(R.string.results_chip_all), formatCount(counts.values.sum())))
    return (listOf(all) + MediaKind.entries.mapNotNull { kind ->
        counts[kind]?.takeIf { it > 0 }?.let { KindChipUi(kind, UiText.res(R.string.results_chip_count, kind.label(), formatCount(it))) }
    }).toImmutableList()
}

fun selectionSummary(selection: Map<Long, Long>): UiText? =
    if (selection.isEmpty()) {
        null
    } else {
        UiText.plural(R.plurals.results_selected, selection.size, formatCount(selection.size), formatBytes(selection.values.sum()))
    }

fun deleteConfirm(selection: Map<Long, Long>) = DeleteConfirmUi(
    count = selection.size,
    title = UiText.plural(R.plurals.results_delete_title, selection.size, formatCount(selection.size)),
    body = UiText.res(R.string.results_delete_body, formatBytes(selection.values.sum())),
)

/** What a delete did, as a snackbar line; null when nothing moved. */
fun removedMessage(count: Int, bytes: Long): UiText? =
    if (count == 0) null else UiText.plural(R.plurals.results_removed, count, formatCount(count), formatBytes(bytes))

fun failedMessage(count: Int): UiText? =
    if (count == 0) null else UiText.plural(R.plurals.results_remove_failed, count, formatCount(count))
