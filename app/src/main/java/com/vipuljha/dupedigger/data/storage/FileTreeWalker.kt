package com.vipuljha.dupedigger.data.storage

import com.vipuljha.dupedigger.domain.model.DiscoveredFile
import com.vipuljha.dupedigger.domain.model.ScanFilter
import com.vipuljha.dupedigger.domain.model.mediaKindOf
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.nio.file.Files

/**
 * Walks folders with `java.io.File`, used for the Deep scan (All files access).
 * - Iterative and depth-first: memory holds the pending folders and one listing, never every file.
 * - Symlinks are skipped, so the walk never leaves its roots or loops.
 * - Checks for cancellation before each directory and each file.
 */
class FileTreeWalker(private val mimeTypeOf: (fileName: String) -> String?) {

    fun walk(roots: List<File>, filter: ScanFilter): Flow<DiscoveredFile> = flow {
        for (root in roots) {
            val pending = ArrayDeque<File>().apply { addLast(root) }
            while (pending.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val dir = pending.removeLast()
                val children = dir.listFiles() ?: continue
                for (child in children) {
                    currentCoroutineContext().ensureActive()
                    val path = child.path
                    if (Files.isSymbolicLink(child.toPath()) || filter.skips(path, child.name)) continue
                    if (child.isDirectory) {
                        pending.addLast(child)
                    } else if (child.isFile) {
                        val mime = mimeTypeOf(child.name)
                        emit(
                            DiscoveredFile(
                                sourceUri = child.toURI().toString(),
                                path = path,
                                name = child.name,
                                mimeType = mime,
                                mediaKind = mediaKindOf(mime),
                                size = child.length(),
                                mtime = child.lastModified(),
                                volume = root.path,
                            ),
                        )
                    }
                }
            }
        }
    }
}
