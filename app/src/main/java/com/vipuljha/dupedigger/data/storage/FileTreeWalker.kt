package com.vipuljha.dupedigger.data.storage

import com.vipuljha.dupedigger.domain.model.DiscoveredFile
import com.vipuljha.dupedigger.domain.model.ScanFilter
import com.vipuljha.dupedigger.domain.model.mediaKindOf
import com.vipuljha.dupedigger.domain.model.mimeTypeForName
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes

/**
 * Walks folders with `java.io.File`, used for the Deep scan (All files access).
 * - Iterative and depth-first: memory holds the pending folders and one listing, never every file.
 * - Symlinks are skipped, so the walk never leaves its roots or loops.
 * - Filtered entries are dropped before any stat call; every other entry costs one attribute read, which also
 *   carries the file key, so a renamed or moved file keeps its index row for free.
 * - Checks for cancellation before each directory and each file.
 */
class FileTreeWalker(private val mimeTypeOf: (fileName: String) -> String?) {

    /** Walks each of [volumes], or only its [folders] (paths relative to the volume) when given. */
    fun walk(volumes: List<File>, filter: ScanFilter, folders: List<String>? = null): Flow<DiscoveredFile> = flow {
        for (volume in volumes) {
            val pending = ArrayDeque(folders?.map { File(volume, it) } ?: listOf(volume))
            while (pending.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val dir = pending.removeLast()
                val children = dir.listFiles() ?: continue
                for (child in children) {
                    currentCoroutineContext().ensureActive()
                    val path = child.path
                    if (filter.skips(path, child.name)) continue
                    val attrs = attributesOf(child) ?: continue
                    if (attrs.isDirectory) {
                        pending.addLast(child)
                    } else if (attrs.isRegularFile) {
                        val mime = mimeTypeOf(child.name) ?: mimeTypeForName(child.name)
                        emit(
                            DiscoveredFile(
                                sourceUri = child.toURI().toString(),
                                path = path,
                                name = child.name,
                                mimeType = mime,
                                mediaKind = mediaKindOf(mime, child.name),
                                size = attrs.size(),
                                mtime = attrs.lastModifiedTime().toMillis(),
                                volume = volume.path,
                                fileKey = attrs.fileKey()?.toString(),
                            ),
                        )
                    }
                }
            }
        }
    }

    /** One stat call per entry, never following a symlink; null for an entry that vanished or can't be read. */
    private fun attributesOf(file: File): BasicFileAttributes? = try {
        Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    } catch (_: IOException) {
        null
    }
}
