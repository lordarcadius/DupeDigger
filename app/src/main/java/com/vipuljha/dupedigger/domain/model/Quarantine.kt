package com.vipuljha.dupedigger.domain.model

/** A non-media file moved out of the way; [originalPath] is where restore puts it back. */
data class QuarantinedFile(val id: Long, val name: String, val originalPath: String, val size: Long, val movedAt: Long)
