package com.vipuljha.dupedigger.data.storage

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.content.ContextCompat
import com.vipuljha.dupedigger.domain.model.MediaAccess
import com.vipuljha.dupedigger.domain.model.StorageAccessState
import com.vipuljha.dupedigger.domain.repository.StorageAccess
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidStorageAccess @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : StorageAccess {

    override val mediaPermissions: List<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> FULL_MEDIA_33 + Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> FULL_MEDIA_33
        else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    override fun current(): StorageAccessState = StorageAccessState(
        media = mediaAccess(),
        allFiles = Environment.isExternalStorageManager(),
    )

    private fun mediaAccess(): MediaAccess {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return if (granted(Manifest.permission.READ_EXTERNAL_STORAGE)) MediaAccess.FULL else MediaAccess.NONE
        }
        val grantedTypes = FULL_MEDIA_33.count(::granted)
        val selectedOnly = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        return when {
            grantedTypes == FULL_MEDIA_33.size -> MediaAccess.FULL
            grantedTypes > 0 || selectedOnly -> MediaAccess.PARTIAL
            else -> MediaAccess.NONE
        }
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        val FULL_MEDIA_33 = listOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
        )
    }
}
