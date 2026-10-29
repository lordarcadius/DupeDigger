package com.vipuljha.dupedigger.domain.repository

/** Reads the plain text of the document formats it supports. */
interface DocumentTextReader {
    /** Whether this file's format can be read on this device; others are skipped, not counted as unreadable. */
    fun supports(name: String, mimeType: String?): Boolean

    /** The document's text, possibly truncated for very large files; null when it can't be read. */
    suspend fun text(sourceUri: String, name: String, mimeType: String?): String?
}
