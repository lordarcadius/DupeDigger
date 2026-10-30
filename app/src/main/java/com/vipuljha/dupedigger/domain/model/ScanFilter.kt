package com.vipuljha.dupedigger.domain.model

/**
 * What every enumerator skips, for a file or a whole folder.
 * - [excludedPaths]: user exclusions, each matching the folder and everything inside it.
 * - `Android/data` and `Android/obb` on every volume: other apps' private files.
 * - Hidden entries (a name starting with '.') when [skipHidden] is set.
 */
class ScanFilter(excludedPaths: Collection<String>, val skipHidden: Boolean) {

    private val excluded = excludedPaths.map { it.trimEnd('/') }.filter { it.isNotEmpty() }

    fun skips(path: String?, name: String): Boolean {
        if (skipHidden && (name.startsWith('.') || path?.contains("/.") == true)) return true
        if (path == null) return false
        return APP_PRIVATE.containsMatchIn(path) || excluded.any { path == it || path.startsWith("$it/") }
    }

    private companion object {
        val APP_PRIVATE = Regex("^/storage/(emulated/\\d+|[^/]+)/Android/(data|obb)(/|$)")
    }
}
