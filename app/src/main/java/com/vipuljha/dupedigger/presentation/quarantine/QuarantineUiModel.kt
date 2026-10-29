package com.vipuljha.dupedigger.presentation.quarantine

import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.core.ui.formatBytes
import com.vipuljha.dupedigger.core.ui.formatCount
import com.vipuljha.dupedigger.core.ui.formatRelativeTime
import com.vipuljha.dupedigger.domain.model.QuarantinedFile

fun QuarantinedFile.toUi(nowMillis: Long) = QuarantineItemUi(
    id = id,
    name = name,
    folder = originalPath.substringBeforeLast('/', "").takeIf { it.isNotEmpty() }?.let { UiText.Raw(it) } ?: UiText.res(R.string.value_unknown),
    details = UiText.res(R.string.quarantine_item_details, formatBytes(size), formatRelativeTime(movedAt, nowMillis)),
)

/** Restored files come back into results on the next scan. */
fun restoreMessage(restored: Int, failed: Int): UiText =
    if (failed == 0) {
        UiText.plural(R.plurals.restored, restored, formatCount(restored))
    } else {
        UiText.plural(R.plurals.restore_failed, failed, formatCount(failed))
    }
