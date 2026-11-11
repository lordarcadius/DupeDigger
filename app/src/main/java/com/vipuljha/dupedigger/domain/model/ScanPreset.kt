package com.vipuljha.dupedigger.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList

/**
 * A saved way to scan: what to walk, and which file types to look for duplicates in.
 * - [kinds] limits the duplicate search, not the index: the whole scope is still indexed, so files of other types
 *   keep their cached work and aren't pruned.
 * - A built-in preset has a [builtIn] key, which the UI names it by, and can't be deleted.
 */
data class ScanPreset(
    val id: Long,
    val name: String,
    val scope: ScanScope,
    val kinds: ImmutableSet<MediaKind>,
    val builtIn: BuiltInPreset? = null,
) {
    init {
        require(kinds.isNotEmpty()) { "A preset needs at least one file type" }
    }

    val deletable: Boolean get() = builtIn == null
}

/** Ready-made presets for the usual app folders; each path is relative to every storage volume's root. */
enum class BuiltInPreset(val paths: ImmutableList<String>, val kinds: ImmutableSet<MediaKind>) {
    WHATSAPP(
        persistentListOf(
            "Android/media/com.whatsapp/WhatsApp/Media",
            "Android/media/com.whatsapp.w4b/WhatsApp Business/Media",
            "WhatsApp/Media",
        ),
        AllMediaKinds,
    ),
    CAMERA(persistentListOf("DCIM/Camera"), PhotosAndVideos),
    TELEGRAM(
        persistentListOf("Telegram", "Android/media/org.telegram.messenger/Telegram"),
        AllMediaKinds
    ),
    SCREENSHOTS(persistentListOf("Pictures/Screenshots", "DCIM/Screenshots"), PhotosAndVideos),
    FACEBOOK(
        persistentListOf("Pictures/Facebook", "DCIM/Facebook", "Movies/Facebook"),
        PhotosAndVideos
    ),
    INSTAGRAM(
        persistentListOf("Pictures/Instagram", "DCIM/Instagram", "Movies/Instagram"),
        PhotosAndVideos
    ),
    SNAPCHAT(
        persistentListOf("Snapchat", "Pictures/Snapchat", "DCIM/Snapchat", "Movies/Snapchat"),
        PhotosAndVideos
    ),
    REDDIT(persistentListOf("Pictures/Reddit", "Movies/Reddit"), PhotosAndVideos),
    ;

    /** Negative ids, so they never collide with a saved preset's. */
    fun toPreset() = ScanPreset(
        id = -(ordinal + 1L),
        name = name,
        scope = ScanScope.Paths(paths),
        kinds = kinds,
        builtIn = this
    )

    companion object {
        val presets: ImmutableList<ScanPreset> = entries.map { it.toPreset() }.toImmutableList()
    }
}

private val PhotosAndVideos: ImmutableSet<MediaKind> =
    persistentSetOf(MediaKind.IMAGE, MediaKind.VIDEO)
