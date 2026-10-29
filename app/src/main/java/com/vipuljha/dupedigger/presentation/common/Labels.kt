package com.vipuljha.dupedigger.presentation.common

import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.ScopeKind

fun MediaKind.label(): UiText = UiText.res(
    when (this) {
        MediaKind.IMAGE -> R.string.kind_images
        MediaKind.VIDEO -> R.string.kind_videos
        MediaKind.AUDIO -> R.string.kind_audio
        MediaKind.DOCUMENT -> R.string.kind_documents
        MediaKind.OTHER -> R.string.kind_other
    },
)

fun ScopeKind.label(): UiText = UiText.res(
    when (this) {
        ScopeKind.WHOLE_DEVICE -> R.string.scope_whole_device
        ScopeKind.MEDIA_ONLY -> R.string.scope_media
        ScopeKind.FOLDERS -> R.string.scope_folders
    },
)
