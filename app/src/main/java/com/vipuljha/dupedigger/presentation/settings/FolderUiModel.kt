package com.vipuljha.dupedigger.presentation.settings

import androidx.compose.runtime.Immutable
import com.vipuljha.dupedigger.domain.model.ScanFolder

/** [key] identifies the row: the tree URI for a scan folder, the path for an exclusion. */
@Immutable
data class FolderUiModel(val key: String, val name: String, val detail: String?)

fun ScanFolder.toUiModel() = FolderUiModel(key = uri, name = name, detail = path)

fun excludedPathToUiModel(path: String) =
    FolderUiModel(key = path, name = path.substringAfterLast('/').ifEmpty { path }, detail = path)
