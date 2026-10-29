package com.vipuljha.dupedigger.di

import android.content.Context
import androidx.room.Room
import com.vipuljha.dupedigger.data.local.db.ALL_MIGRATIONS
import com.vipuljha.dupedigger.data.local.db.DupeDiggerDatabase
import com.vipuljha.dupedigger.data.local.db.dao.DuplicateGroupDao
import com.vipuljha.dupedigger.data.local.db.dao.EmbeddingDao
import com.vipuljha.dupedigger.data.local.db.dao.ExcludedPathDao
import com.vipuljha.dupedigger.data.local.db.dao.FileHashDao
import com.vipuljha.dupedigger.data.local.db.dao.FileIndexDao
import com.vipuljha.dupedigger.data.local.db.dao.IgnoredGroupDao
import com.vipuljha.dupedigger.data.local.db.dao.KeepChoiceDao
import com.vipuljha.dupedigger.data.local.db.dao.PerceptualHashDao
import com.vipuljha.dupedigger.data.local.db.dao.QuarantineDao
import com.vipuljha.dupedigger.data.local.db.dao.ScanRunDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): DupeDiggerDatabase =
        Room.databaseBuilder(context, DupeDiggerDatabase::class.java, DupeDiggerDatabase.NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .build()

    @Provides fun provideFileIndexDao(db: DupeDiggerDatabase): FileIndexDao = db.fileIndexDao()
    @Provides fun provideFileHashDao(db: DupeDiggerDatabase): FileHashDao = db.fileHashDao()
    @Provides fun providePerceptualHashDao(db: DupeDiggerDatabase): PerceptualHashDao = db.perceptualHashDao()
    @Provides fun provideEmbeddingDao(db: DupeDiggerDatabase): EmbeddingDao = db.embeddingDao()
    @Provides fun provideDuplicateGroupDao(db: DupeDiggerDatabase): DuplicateGroupDao = db.duplicateGroupDao()
    @Provides fun provideScanRunDao(db: DupeDiggerDatabase): ScanRunDao = db.scanRunDao()
    @Provides fun provideIgnoredGroupDao(db: DupeDiggerDatabase): IgnoredGroupDao = db.ignoredGroupDao()
    @Provides fun provideKeepChoiceDao(db: DupeDiggerDatabase): KeepChoiceDao = db.keepChoiceDao()
    @Provides fun provideQuarantineDao(db: DupeDiggerDatabase): QuarantineDao = db.quarantineDao()
    @Provides fun provideExcludedPathDao(db: DupeDiggerDatabase): ExcludedPathDao = db.excludedPathDao()
}
