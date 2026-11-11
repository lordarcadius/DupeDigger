package com.vipuljha.dupedigger.domain.repository

/** Checks that indexed files are still on the device, for rows a scan that didn't cover them left behind. */
interface FilePresence {
    /** Of [sourceUris], the ones nothing can be read from any more. */
    suspend fun missing(sourceUris: Collection<String>): Set<String>
}
