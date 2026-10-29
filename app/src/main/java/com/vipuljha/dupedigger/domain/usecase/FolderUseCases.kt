package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.domain.repository.ExcludedPathRepository
import com.vipuljha.dupedigger.domain.repository.ScanFolderRepository
import javax.inject.Inject

class AddScanFolderUseCase @Inject constructor(private val folders: ScanFolderRepository) {
    suspend operator fun invoke(treeUri: String) = folders.add(treeUri)
}

class RemoveScanFolderUseCase @Inject constructor(private val folders: ScanFolderRepository) {
    suspend operator fun invoke(treeUri: String) = folders.remove(treeUri)
}

class ExcludeFolderUseCase @Inject constructor(private val excluded: ExcludedPathRepository) {
    suspend operator fun invoke(treeUri: String) = excluded.excludeTree(treeUri)
}

class RemoveExcludedPathUseCase @Inject constructor(private val excluded: ExcludedPathRepository) {
    suspend operator fun invoke(path: String) = excluded.remove(path)
}
