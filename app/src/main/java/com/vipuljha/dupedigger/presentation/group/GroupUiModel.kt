package com.vipuljha.dupedigger.presentation.group

import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.core.ui.formatBytes
import com.vipuljha.dupedigger.core.ui.formatCount
import com.vipuljha.dupedigger.domain.model.DuplicateGroupItem
import com.vipuljha.dupedigger.domain.model.GroupMember
import com.vipuljha.dupedigger.domain.model.MediaKind
import kotlinx.collections.immutable.toImmutableList
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** The keeper first, then the files that could go. */
fun DuplicateGroupItem.toDetailUi(): GroupDetailUi {
    val copies = removable.size + 1
    return GroupDetailUi(
        title = keeper.displayName(),
        summary = UiText.plural(R.plurals.group_summary, copies, formatCount(copies), formatBytes(reclaimableBytes)),
        members = (listOf(keeper) + removable).map { it.toMemberUi(kind, isKeeper = it.fileId == keeper.fileId) }.toImmutableList(),
    )
}

private fun GroupMember.toMemberUi(kind: MediaKind, isKeeper: Boolean) = MemberUi(
    fileId = fileId,
    kind = kind,
    name = displayName(),
    location = path?.substringBeforeLast('/', "")?.takeIf { it.isNotEmpty() }?.let { UiText.Raw(it) } ?: UiText.res(R.string.value_unknown),
    details = UiText.res(R.string.group_member_details, formatBytes(size), modifiedDate(mtime)),
    isKeeper = isKeeper,
    thumbnail = sourceUri.takeIf { kind == MediaKind.IMAGE || kind == MediaKind.VIDEO },
)

private fun GroupMember.displayName() = name.ifEmpty { sourceUri.substringAfterLast('/') }

private fun modifiedDate(mtime: Long): UiText =
    if (mtime <= 0) {
        UiText.res(R.string.value_unknown)
    } else {
        UiText.Raw(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).format(Instant.ofEpochMilli(mtime).atZone(ZoneId.systemDefault())))
    }
