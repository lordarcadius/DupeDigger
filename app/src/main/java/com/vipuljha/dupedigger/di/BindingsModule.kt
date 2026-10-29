package com.vipuljha.dupedigger.di

import com.vipuljha.dupedigger.data.hashing.StreamingContentHasher
import com.vipuljha.dupedigger.data.local.prefs.SettingsRepositoryImpl
import com.vipuljha.dupedigger.data.repository.DuplicateGroupRepositoryImpl
import com.vipuljha.dupedigger.data.repository.ExcludedPathRepositoryImpl
import com.vipuljha.dupedigger.data.repository.FileIndexRepositoryImpl
import com.vipuljha.dupedigger.data.repository.ScanRunRepositoryImpl
import com.vipuljha.dupedigger.data.storage.AndroidFileEnumerator
import com.vipuljha.dupedigger.data.storage.AndroidFileInspector
import com.vipuljha.dupedigger.data.storage.AndroidFileRemover
import com.vipuljha.dupedigger.data.storage.AndroidScanFolderRepository
import com.vipuljha.dupedigger.data.storage.AndroidStorageAccess
import com.vipuljha.dupedigger.data.worker.WorkManagerScanScheduler
import com.vipuljha.dupedigger.domain.repository.ContentHasher
import com.vipuljha.dupedigger.domain.repository.DuplicateGroupRepository
import com.vipuljha.dupedigger.domain.repository.ExcludedPathRepository
import com.vipuljha.dupedigger.domain.repository.FileEnumerator
import com.vipuljha.dupedigger.domain.repository.FileIndexRepository
import com.vipuljha.dupedigger.domain.repository.FileInspector
import com.vipuljha.dupedigger.domain.repository.FileRemover
import com.vipuljha.dupedigger.domain.repository.ScanFolderRepository
import com.vipuljha.dupedigger.domain.repository.ScanRunRepository
import com.vipuljha.dupedigger.domain.repository.ScanScheduler
import com.vipuljha.dupedigger.domain.repository.SettingsRepository
import com.vipuljha.dupedigger.domain.repository.StorageAccess
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {

    @Binds
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    abstract fun bindStorageAccess(impl: AndroidStorageAccess): StorageAccess

    @Binds
    abstract fun bindScanFolderRepository(impl: AndroidScanFolderRepository): ScanFolderRepository

    @Binds
    abstract fun bindExcludedPathRepository(impl: ExcludedPathRepositoryImpl): ExcludedPathRepository

    @Binds
    abstract fun bindFileEnumerator(impl: AndroidFileEnumerator): FileEnumerator

    @Binds
    abstract fun bindFileIndexRepository(impl: FileIndexRepositoryImpl): FileIndexRepository

    @Binds
    abstract fun bindContentHasher(impl: StreamingContentHasher): ContentHasher

    @Binds
    abstract fun bindScanRunRepository(impl: ScanRunRepositoryImpl): ScanRunRepository

    @Binds
    abstract fun bindScanScheduler(impl: WorkManagerScanScheduler): ScanScheduler

    @Binds
    abstract fun bindDuplicateGroupRepository(impl: DuplicateGroupRepositoryImpl): DuplicateGroupRepository

    @Binds
    abstract fun bindFileRemover(impl: AndroidFileRemover): FileRemover

    @Binds
    abstract fun bindFileInspector(impl: AndroidFileInspector): FileInspector
}
