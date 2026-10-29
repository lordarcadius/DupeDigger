package com.vipuljha.dupedigger.presentation.compare

import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.core.ui.formatBytes
import com.vipuljha.dupedigger.domain.model.DuplicateGroupItem
import com.vipuljha.dupedigger.domain.model.GroupMember
import com.vipuljha.dupedigger.domain.model.MatchTier
import com.vipuljha.dupedigger.presentation.common.tierTag

/** Null when either file is no longer in the group. */
fun DuplicateGroupItem.toCompareUi(leftId: Long, rightId: Long): CompareUi? {
    val members = (listOf(keeper) + removable).associateBy { it.fileId }
    val left = members[leftId] ?: return null
    val right = members[rightId] ?: return null
    return CompareUi(left.toPane(), right.toPane(), tierTag(tier, score).takeIf { tier != MatchTier.EXACT })
}

private fun GroupMember.toPane() = ComparePaneUi(
    name = name.ifEmpty { sourceUri.substringAfterLast('/') },
    uri = sourceUri,
    details = UiText.res(R.string.compare_pane_details, formatBytes(size), path?.substringBeforeLast('/', "") ?: ""),
)
