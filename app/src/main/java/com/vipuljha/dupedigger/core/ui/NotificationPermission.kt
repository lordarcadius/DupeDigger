package com.vipuljha.dupedigger.core.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Runs an action after asking once for the notification permission on API 33+, so a scan can show its progress.
 * The action runs whatever the answer: a scan works without the notification.
 */
@Composable
fun rememberNotificationPermissionGate(): (action: () -> Unit) -> Unit {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pending?.invoke()
        pending = null
    }
    return remember(context, launcher) {
        { action ->
            val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                action()
            } else {
                pending = action
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
