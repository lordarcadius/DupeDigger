package com.vipuljha.dupedigger.domain.repository

import com.vipuljha.dupedigger.domain.model.GroupMember
import com.vipuljha.dupedigger.domain.model.MediaInfo
import com.vipuljha.dupedigger.domain.model.MediaKind
import com.vipuljha.dupedigger.domain.model.ViewTarget

/** Reads what the index doesn't hold, from the file itself. */
interface FileInspector {
    /** Null when the file can't be read or isn't an image or video. */
    suspend fun mediaInfo(member: GroupMember, kind: MediaKind): MediaInfo?

    /** Null when no app-shareable URI can be made for the file. */
    suspend fun viewTarget(member: GroupMember): ViewTarget?
}
