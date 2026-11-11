package com.vipuljha.dupedigger.data.storage

import android.content.Context
import android.os.storage.StorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** The root folder of every mounted storage volume, read fresh on each call so a removed SD card drops out. */
@Singleton
class StorageVolumes @Inject constructor(@param:ApplicationContext private val context: Context) {
    fun roots(): List<File> =
        context.getSystemService(StorageManager::class.java).storageVolumes.mapNotNull { it.directory }
}
