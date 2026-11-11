package com.vipuljha.dupedigger.presentation.results

import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.core.ui.formatBytes
import com.vipuljha.dupedigger.core.ui.formatCount
import com.vipuljha.dupedigger.domain.model.DuplicateFolder
import com.vipuljha.dupedigger.domain.model.DuplicateGroupItem
import com.vipuljha.dupedigger.domain.model.GroupFacet
import com.vipuljha.dupedigger.domain.model.MatchTier
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.StorageUsage
import com.vipuljha.dupedigger.domain.model.matching
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
        tier = tier,
        thumbnail = keeper.sourceUri.takeIf { kind == MediaKind.IMAGE || kind == MediaKind.VIDEO },
        title = keeper.name.ifEmpty { keeper.sourceUri.substringAfterLast('/') },
        detail = if (exact) {
            UiText.res(
                R.string.results_row_detail,
                UiText.plural(
                    R.plurals.results_copies,
                    files,
                    formatCount(files),
                    formatBytes(keeper.size)
                ),
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

private fun countLabel(label: UiText, count: Int) =
    UiText.res(R.string.results_chip_count, label, formatCount(count))

/**
 * "All" then each tier with groups left after the kind filter, in a fixed order, each with its group count.
 * The chosen tier always stays on the row even at zero, so the user can always switch away from an empty view.
 */
fun tierChips(
    facets: List<GroupFacet>,
    kind: MediaKind?,
    selected: MatchTier?
): ImmutableList<TierChipUi> {
    val counts = facets.matching(kind = kind).groupingBy { it.tier }
        .fold(0) { sum, facet -> sum + facet.groups }
    val all =
        TierChipUi(null, countLabel(UiText.res(R.string.results_chip_all), counts.values.sum()))
    return (listOf(all) + MatchTier.entries.mapNotNull { tier ->
        val count = counts[tier] ?: 0
        if (count == 0 && tier != selected) null else TierChipUi(
            tier,
            countLabel(tier.label(), count)
        )
    }).toImmutableList()
}

/** "All" then each kind with groups left after the tier filter, counted the same way as [tierChips]. */
fun kindChips(
    facets: List<GroupFacet>,
    tier: MatchTier?,
    selected: MediaKind?
): ImmutableList<KindChipUi> {
    val counts = facets.matching(tier = tier).groupingBy { it.kind }
        .fold(0) { sum, facet -> sum + facet.groups }
    val all =
        KindChipUi(null, countLabel(UiText.res(R.string.results_chip_all), counts.values.sum()))
    return (listOf(all) + MediaKind.entries.mapNotNull { kind ->
        val count = counts[kind] ?: 0
        if (count == 0 && kind != selected) null else KindChipUi(
            kind,
            countLabel(kind.label(), count)
        )
    }).toImmutableList()
}

/** Everything "Select all" would pick under the current filter, counted from the same facets as the chips. */
fun selectableUi(facets: List<GroupFacet>, kind: MediaKind?, tier: MatchTier?): SelectableUi {
    val matching = facets.matching(kind, tier)
    val files = matching.sumOf { it.files }
    val bytes = matching.sumOf { it.bytes }
    return SelectableUi(
        files,
        bytes,
        UiText.plural(
            R.plurals.results_select_all_detail,
            files,
            formatCount(files),
            formatBytes(bytes)
        )
    )
}

fun selectionSummary(selection: Map<Long, SelectedUi>): UiText? =
    if (selection.isEmpty()) {
        null
    } else {
        UiText.plural(
            R.plurals.results_selected,
            selection.size,
            formatCount(selection.size),
            formatBytes(selection.values.sumOf { it.size }),
        )
    }

/**
 * The delete confirmation: how many files, how much it frees, and how much free space that leaves.
 * [similar] counts files from near and AI groups, which are alike rather than byte-identical and so are called out.
 */
fun deleteConfirm(
    selection: Map<Long, SelectedUi>,
    similar: Int,
    usage: StorageUsage?
): DeleteConfirmUi {
    val bytes = selection.values.sumOf { it.size }
    return DeleteConfirmUi(
        count = selection.size,
        title = UiText.plural(
            R.plurals.results_delete_title,
            selection.size,
            formatCount(selection.size)
        ),
        body = UiText.res(R.string.results_delete_body, formatBytes(bytes)),
        freeAfter = usage?.takeIf { it.totalBytes > 0 }?.let {
            UiText.res(
                R.string.results_delete_free_after,
                formatBytes(it.freeBytes + bytes),
                formatBytes(it.totalBytes)
            )
        },
        similarWarning = if (similar == 0) null else UiText.plural(
            R.plurals.results_delete_similar,
            similar,
            formatCount(similar)
        ),
    )
}

/** What a delete did, as a snackbar line; null when nothing moved. */
fun removedMessage(count: Int, bytes: Long): UiText? =
    if (count == 0) null else UiText.plural(
        R.plurals.results_removed,
        count,
        formatCount(count),
        formatBytes(bytes)
    )

fun failedMessage(count: Int): UiText? =
    if (count == 0) null else UiText.plural(
        R.plurals.results_remove_failed,
        count,
        formatCount(count)
    )

/** A folder named by its last segment, with how many group members it holds and its full path. */
fun DuplicateFolder.toChoice() = FolderChoiceUi(
    path = path,
    name = path.substringAfterLast('/'),
    detail = UiText.res(
        R.string.results_folder_detail,
        UiText.plural(R.plurals.results_folder_files, files, formatCount(files)),
        path
    ),
)
