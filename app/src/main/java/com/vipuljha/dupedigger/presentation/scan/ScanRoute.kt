package com.vipuljha.dupedigger.presentation.scan

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vipuljha.dupedigger.core.mvi.ObserveAsEvents
import com.vipuljha.dupedigger.core.ui.asString
import com.vipuljha.dupedigger.core.ui.rememberNotificationPermissionGate
import kotlinx.coroutines.launch

@Composable
fun ScanRoute(
    onBack: () -> Unit,
    onViewResults: () -> Unit,
    viewModel: ScanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val progress = viewModel.progress.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val withNotificationPermission = rememberNotificationPermissionGate()
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        viewModel.onIntent(ScanIntent.FolderPicked(uri?.toString()))
    }
    val inLaterStep = state.phase == ScanPhase.Setup && state.step != SetupStep.Presets
    BackHandler(enabled = inLaterStep) { viewModel.onIntent(ScanIntent.BackClicked) }

    LifecycleResumeEffect(Unit) {
        viewModel.onIntent(ScanIntent.Refresh)
        onPauseOrDispose {}
    }
    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            ScanEffect.ViewResults -> onViewResults()
            ScanEffect.PickFolder -> pickFolder.launch(null)
            is ScanEffect.ShowMessage -> scope.launch { snackbarHostState.showSnackbar(effect.message.asString(context)) }
        }
    }

    ScanScreen(
        state = state,
        progress = { progress.value },
        onIntent = { intent ->
            if (intent == ScanIntent.StartClicked) withNotificationPermission { viewModel.onIntent(intent) } else viewModel.onIntent(intent)
        },
        snackbarHostState = snackbarHostState,
        onBack = { if (inLaterStep) viewModel.onIntent(ScanIntent.BackClicked) else onBack() },
    )
}
