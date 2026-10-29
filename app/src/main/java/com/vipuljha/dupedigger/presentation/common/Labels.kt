package com.vipuljha.dupedigger.presentation.common

import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.ui.UiText
import com.vipuljha.dupedigger.core.ui.formatSimilarity
import com.vipuljha.dupedigger.domain.model.MatchTier
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

/** "Similar · 96%": a near or AI group's tier and similarity, never shown as 100% so it can't read as exact. */
fun tierTag(tier: MatchTier, score: Float): UiText = UiText.res(R.string.results_tier_tag, tier.label(), formatSimilarity(score))

fun MatchTier.label(): UiText = UiText.res(
    when (this) {
        MatchTier.EXACT -> R.string.results_tier_exact
        MatchTier.NEAR -> R.string.results_tier_near
        MatchTier.SEMANTIC -> R.string.results_tier_semantic
    },
)
