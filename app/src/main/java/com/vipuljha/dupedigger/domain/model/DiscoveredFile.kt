package com.vipuljha.dupedigger.domain.model

enum class MediaKind { IMAGE, VIDEO, AUDIO, DOCUMENT, OTHER }

/** One file found by an enumerator; [sourceUri] identifies it across scans, [mtime] is epoch millis. */
data class DiscoveredFile(
    val sourceUri: String,
    val path: String?,
    val name: String,
    val mimeType: String?,
    val mediaKind: MediaKind,
    val size: Long,
    val mtime: Long,
    val volume: String?,
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
