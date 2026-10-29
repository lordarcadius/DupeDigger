package com.vipuljha.dupedigger.di

import com.vipuljha.dupedigger.data.local.prefs.SettingsRepositoryImpl
import com.vipuljha.dupedigger.data.storage.AndroidStorageAccess
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
}
