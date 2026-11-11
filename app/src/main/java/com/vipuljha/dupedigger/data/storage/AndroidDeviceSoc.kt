package com.vipuljha.dupedigger.data.storage

import android.content.Context
import android.os.Build
import com.vipuljha.dupedigger.domain.repository.DeviceSoc
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `Build.SOC_MODEL` is the same `ro.soc.model` LiteRT's own instructions tell you to read, and it exists from
 * API 31, which is this app's minimum. A device that doesn't set it reports `unknown`, which matches no build.
 */
@Singleton
class AndroidDeviceSoc @Inject constructor(@param:ApplicationContext private val context: Context) : DeviceSoc {

    override fun model(): String = Build.SOC_MODEL.takeUnless { it == Build.UNKNOWN }.orEmpty()

    override fun nativeLibraryDir(): String = context.applicationInfo.nativeLibraryDir
}
