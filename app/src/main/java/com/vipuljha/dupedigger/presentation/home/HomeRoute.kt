package com.vipuljha.dupedigger.presentation.home

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
fun HomeRoute(
    onMenuClick: (() -> Unit)?,
    onOpenScan: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val withNotificationPermission = rememberNotificationPermissionGate()
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        viewModel.onIntent(HomeIntent.FolderPicked(uri?.toString()))
    }

    LifecycleResumeEffect(Unit) {
        viewModel.onIntent(HomeIntent.Refresh)
        onPauseOrDispose {}
    }
    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            HomeEffect.OpenScan -> onOpenScan()
            HomeEffect.PickFolder -> pickFolder.launch(null)
            is HomeEffect.ShowMessage -> scope.launch { snackbarHostState.showSnackbar(effect.message.asString(context)) }
        }
    }

    HomeScreen(
        state = state,
        onIntent = { intent ->
            if (intent is HomeIntent.QuickActionClicked) withNotificationPermission { viewModel.onIntent(intent) } else viewModel.onIntent(intent)
        },
        snackbarHostState = snackbarHostState,
        onMenuClick = onMenuClick,
    )
}
