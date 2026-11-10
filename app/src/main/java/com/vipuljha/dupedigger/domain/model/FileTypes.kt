package com.vipuljha.dupedigger.domain.model

/**
 * File kinds and MIME types taken from a file name, for the many extensions the platform's `MimeTypeMap` doesn't know.
 * Without this a HEIC photo or a Matroska video from the deep scan has no MIME type, lands in [MediaKind.OTHER], and
 * is then left out of the image and video tiers and of a scan that asks only for photos.
 */
private val imageExtensions = setOf(
    "jpg", "jpeg", "jpe", "jfif", "png", "apng", "gif", "bmp", "dib", "webp", "heic", "heif", "heics", "heifs", "hif",
    "avif", "avifs", "jxl", "tif", "tiff", "ico", "svg", "psd", "xcf",
    "dng", "cr2", "cr3", "nef", "nrw", "arw", "srf", "sr2", "raf", "rw2", "orf", "pef", "raw", "3fr", "erf", "k25",
)

private val videoExtensions = setOf(
    "mp4", "m4v", "mov", "qt", "mkv", "webm", "avi", "wmv", "asf", "flv", "f4v", "3gp", "3gpp", "3g2",
    "mpg", "mpeg", "mpe", "m2v", "mts", "m2ts", "ts", "ogv", "rm", "rmvb", "divx", "vob", "insv",
)

private val audioExtensions = setOf(
    "mp3", "m4a", "m4b", "m4r", "aac", "flac", "wav", "wave", "ogg", "oga", "opus", "wma", "aiff", "aif", "aifc",
    "amr", "awb", "ape", "mka", "mid", "midi", "dsf", "dff", "wv", "ra", "au", "8svx",
)

private val documentExtensions = setOf(
    "txt", "text", "md", "markdown", "rst", "rtf", "log", "csv", "tsv", "json", "xml", "yaml", "yml", "ini", "cfg",
    "html", "htm", "xhtml", "pdf", "doc", "docx", "dot", "dotx", "xls", "xlsx", "xlsm", "ppt", "pptx", "pps", "ppsx",
    "odt", "ods", "odp", "odg", "epub", "mobi", "azw", "azw3", "fb2", "djvu", "djv", "pages", "numbers", "key", "tex",
)

/** MIME types for the extensions that matter to a later stage (decoding, text extraction, sharing). */
private val mimeTypesByExtension = mapOf(
    "heic" to "image/heic", "heif" to "image/heif", "heics" to "image/heic-sequence", "heifs" to "image/heif-sequence",
    "hif" to "image/heif", "avif" to "image/avif", "avifs" to "image/avif-sequence", "jxl" to "image/jxl",
    "jfif" to "image/jpeg", "jpe" to "image/jpeg", "apng" to "image/apng", "dib" to "image/bmp", "dng" to "image/x-adobe-dng",
    "mkv" to "video/x-matroska", "m2ts" to "video/mp2t", "mts" to "video/mp2t", "ts" to "video/mp2t",
    "f4v" to "video/x-f4v", "insv" to "video/mp4", "divx" to "video/avi", "rmvb" to "video/vnd.rn-realvideo",
    "opus" to "audio/opus", "m4b" to "audio/mp4", "m4r" to "audio/mp4", "dsf" to "audio/x-dsf", "dff" to "audio/x-dff",
    "wv" to "audio/x-wavpack", "aifc" to "audio/x-aiff", "wave" to "audio/x-wav",
    "md" to "text/markdown", "markdown" to "text/markdown", "rst" to "text/x-rst", "log" to "text/plain",
    "text" to "text/plain", "tsv" to "text/tab-separated-values", "yaml" to "text/yaml", "yml" to "text/yaml",
    "ini" to "text/plain", "cfg" to "text/plain", "json" to "application/json", "azw3" to "application/vnd.amazon.ebook",
    "fb2" to "application/x-fictionbook+xml", "djvu" to "image/vnd.djvu", "djv" to "image/vnd.djvu",
)

/** The lowercase extension of [fileName], or "" when it has none. A leading dot alone isn't an extension. */
fun extensionOf(fileName: String): String {
    val dot = fileName.lastIndexOf('.')
    if (dot <= 0 || dot == fileName.lastIndex) return ""
    return fileName.substring(dot + 1).lowercase()
}

/** A MIME type for [fileName] when the table knows its extension; null otherwise. */
fun mimeTypeForName(fileName: String): String? = mimeTypesByExtension[extensionOf(fileName)]

/** The kind [fileName]'s extension implies, [MediaKind.OTHER] when the table doesn't know it. */
fun mediaKindForName(fileName: String): MediaKind = when (extensionOf(fileName)) {
    in imageExtensions -> MediaKind.IMAGE
    in videoExtensions -> MediaKind.VIDEO
    in audioExtensions -> MediaKind.AUDIO
    in documentExtensions -> MediaKind.DOCUMENT
    else -> MediaKind.OTHER
}
