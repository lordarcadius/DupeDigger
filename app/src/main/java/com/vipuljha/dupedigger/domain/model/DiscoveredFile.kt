package com.vipuljha.dupedigger.domain.model

import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.toImmutableSet

enum class MediaKind { IMAGE, VIDEO, AUDIO, DOCUMENT, OTHER }

val AllMediaKinds: ImmutableSet<MediaKind> = MediaKind.entries.toImmutableSet()

/**
 * One file found by an enumerator; [sourceUri] identifies it across scans, [mtime] is epoch millis.
 * [fileKey] is the filesystem's own identity for the file, where the enumerator can read it. It survives a rename
 * or a move, which [sourceUri] does not, so the index can keep what the file already cost to hash.
 */
data class DiscoveredFile(
    val sourceUri: String,
    val path: String?,
    val name: String,
    val mimeType: String?,
    val mediaKind: MediaKind,
    val size: Long,
    val mtime: Long,
    val volume: String?,
    val fileKey: String? = null,
)

private val documentMimeTypes = setOf(
    "application/pdf",
    "application/msword",
    "application/rtf",
    "application/epub+zip",
    "application/vnd.oasis.opendocument.text",
    "application/vnd.oasis.opendocument.spreadsheet",
    "application/vnd.oasis.opendocument.presentation",
    "application/vnd.ms-excel",
    "application/vnd.ms-powerpoint",
)

fun mediaKindOf(mimeType: String?): MediaKind {
    val mime = mimeType?.lowercase() ?: return MediaKind.OTHER
    return when {
        mime.startsWith("image/") -> MediaKind.IMAGE
        mime.startsWith("video/") -> MediaKind.VIDEO
        mime.startsWith("audio/") -> MediaKind.AUDIO
        mime.startsWith("text/") || mime in documentMimeTypes ||
            mime.startsWith("application/vnd.openxmlformats-officedocument.") -> MediaKind.DOCUMENT
        else -> MediaKind.OTHER
    }
}

/**
 * The kind of a file the provider gave a MIME type for, falling back to [fileName]'s extension when the type is
 * missing or too generic to place: SAF often answers `application/octet-stream`, and `MimeTypeMap` knows no HEIC.
 */
fun mediaKindOf(mimeType: String?, fileName: String): MediaKind =
    mediaKindOf(mimeType).takeIf { it != MediaKind.OTHER } ?: mediaKindForName(fileName)
