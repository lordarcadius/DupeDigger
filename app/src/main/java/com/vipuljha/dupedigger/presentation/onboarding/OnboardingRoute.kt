package com.vipuljha.dupedigger.presentation.onboarding

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vipuljha.dupedigger.core.mvi.ObserveAsEvents

@Composable
fun OnboardingRoute(
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        val denied = results.filterValues { granted -> !granted }.keys
        val canAskAgain = activity != null && denied.any(activity::shouldShowRequestPermissionRationale)
        viewModel.onIntent(OnboardingIntent.MediaPermissionResult(canAskAgain))
    }

    LifecycleResumeEffect(Unit) {
        viewModel.onIntent(OnboardingIntent.Refresh)
        onPauseOrDispose {}
    }

    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            is OnboardingEffect.RequestMediaPermissions -> permissionLauncher.launch(effect.permissions.toTypedArray())
            OnboardingEffect.OpenAllFilesAccessSettings -> {
                val appScreen = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.fromParts("package", context.packageName, null))
                try {
                    context.startActivity(appScreen)
                } catch (_: ActivityNotFoundException) {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            }
            OnboardingEffect.OpenAppSettings -> context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        }
    }

    OnboardingScreen(state = state, onIntent = viewModel::onIntent)
}
