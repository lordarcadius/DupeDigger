package com.vipuljha.dupedigger.di

import com.vipuljha.dupedigger.data.ai.AndroidEmbeddingInputReader
import com.vipuljha.dupedigger.data.ai.LiteRtEmbedder
import com.vipuljha.dupedigger.data.ai.LiteRtModelLoader
import com.vipuljha.dupedigger.data.ai.ModelLoader
import com.vipuljha.dupedigger.data.hashing.AndroidAudioTagReader
import com.vipuljha.dupedigger.data.hashing.AndroidDocumentTextReader
import com.vipuljha.dupedigger.data.hashing.AndroidImageHasher
import com.vipuljha.dupedigger.data.hashing.AndroidVideoHasher
import com.vipuljha.dupedigger.data.hashing.StreamingContentHasher
import com.vipuljha.dupedigger.data.local.prefs.SettingsRepositoryImpl
import com.vipuljha.dupedigger.data.repository.DuplicateGroupRepositoryImpl
import com.vipuljha.dupedigger.data.repository.ExcludedPathRepositoryImpl
import com.vipuljha.dupedigger.data.repository.FileIndexRepositoryImpl
import com.vipuljha.dupedigger.data.repository.QuarantineRepositoryImpl
import com.vipuljha.dupedigger.data.repository.ScanPresetRepositoryImpl
import com.vipuljha.dupedigger.data.repository.ScanRunRepositoryImpl
import com.vipuljha.dupedigger.data.storage.AndroidDeviceSoc
import com.vipuljha.dupedigger.data.storage.AndroidDeviceStorage
import com.vipuljha.dupedigger.data.storage.AndroidFileEnumerator
import com.vipuljha.dupedigger.data.storage.AndroidFileInspector
import com.vipuljha.dupedigger.data.storage.AndroidFilePresence
import com.vipuljha.dupedigger.data.storage.AndroidFileRemover
import com.vipuljha.dupedigger.data.storage.AndroidPowerState
import com.vipuljha.dupedigger.data.storage.AndroidScanFolderRepository
import com.vipuljha.dupedigger.data.storage.AndroidStorageAccess
import com.vipuljha.dupedigger.data.worker.InMemorySemanticStageSkip
import com.vipuljha.dupedigger.data.worker.WorkManagerModelDownloads
import com.vipuljha.dupedigger.data.worker.WorkManagerScanScheduler
import com.vipuljha.dupedigger.domain.repository.AudioTagReader
import com.vipuljha.dupedigger.domain.repository.ContentHasher
import com.vipuljha.dupedigger.domain.repository.DeviceSoc
import com.vipuljha.dupedigger.domain.repository.DeviceStorage
import com.vipuljha.dupedigger.domain.repository.DocumentTextReader
import com.vipuljha.dupedigger.domain.repository.DuplicateGroupRepository
import com.vipuljha.dupedigger.domain.repository.Embedder
import com.vipuljha.dupedigger.domain.repository.EmbeddingInputReader
import com.vipuljha.dupedigger.domain.repository.ExcludedPathRepository
import com.vipuljha.dupedigger.domain.repository.FileEnumerator
import com.vipuljha.dupedigger.domain.repository.FileIndexRepository
import com.vipuljha.dupedigger.domain.repository.FileInspector
import com.vipuljha.dupedigger.domain.repository.FilePresence
import com.vipuljha.dupedigger.domain.repository.FileRemover
import com.vipuljha.dupedigger.domain.repository.ImageHasher
import com.vipuljha.dupedigger.domain.repository.ModelDownloads
import com.vipuljha.dupedigger.domain.repository.PowerState
import com.vipuljha.dupedigger.domain.repository.QuarantineRepository
import com.vipuljha.dupedigger.domain.repository.ScanFolderRepository
import com.vipuljha.dupedigger.domain.repository.ScanPresetRepository
import com.vipuljha.dupedigger.domain.repository.ScanRunRepository
import com.vipuljha.dupedigger.domain.repository.ScanScheduler
import com.vipuljha.dupedigger.domain.repository.SemanticStageSkip
import com.vipuljha.dupedigger.domain.repository.SettingsRepository
import com.vipuljha.dupedigger.domain.repository.StorageAccess
import com.vipuljha.dupedigger.domain.repository.VideoHasher
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
    abstract fun bindDeviceStorage(impl: AndroidDeviceStorage): DeviceStorage

    @Binds
    abstract fun bindScanFolderRepository(impl: AndroidScanFolderRepository): ScanFolderRepository

    @Binds
    abstract fun bindExcludedPathRepository(impl: ExcludedPathRepositoryImpl): ExcludedPathRepository

    @Binds
    abstract fun bindScanPresetRepository(impl: ScanPresetRepositoryImpl): ScanPresetRepository

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
    abstract fun bindSemanticStageSkip(impl: InMemorySemanticStageSkip): SemanticStageSkip

    @Binds
    abstract fun bindDuplicateGroupRepository(impl: DuplicateGroupRepositoryImpl): DuplicateGroupRepository

    @Binds
    abstract fun bindFileRemover(impl: AndroidFileRemover): FileRemover

    @Binds
    abstract fun bindFileInspector(impl: AndroidFileInspector): FileInspector

    @Binds
    abstract fun bindFilePresence(impl: AndroidFilePresence): FilePresence

    @Binds
    abstract fun bindDeviceSoc(impl: AndroidDeviceSoc): DeviceSoc

    @Binds
    abstract fun bindImageHasher(impl: AndroidImageHasher): ImageHasher

    @Binds
    abstract fun bindVideoHasher(impl: AndroidVideoHasher): VideoHasher

    @Binds
    abstract fun bindDocumentTextReader(impl: AndroidDocumentTextReader): DocumentTextReader

    @Binds
    abstract fun bindAudioTagReader(impl: AndroidAudioTagReader): AudioTagReader

    @Binds
    abstract fun bindModelDownloads(impl: WorkManagerModelDownloads): ModelDownloads

    @Binds
    abstract fun bindEmbedder(impl: LiteRtEmbedder): Embedder

    @Binds
    abstract fun bindEmbeddingInputReader(impl: AndroidEmbeddingInputReader): EmbeddingInputReader

    @Binds
    abstract fun bindModelLoader(impl: LiteRtModelLoader): ModelLoader

    @Binds
    abstract fun bindPowerState(impl: AndroidPowerState): PowerState

    @Binds
    abstract fun bindQuarantineRepository(impl: QuarantineRepositoryImpl): QuarantineRepository
}
