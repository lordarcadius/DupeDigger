package com.vipuljha.dupedigger.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vipuljha.dupedigger.core.mvi.ObserveAsEvents
import com.vipuljha.dupedigger.core.ui.asString
import kotlinx.coroutines.launch

@Composable
fun SettingsRoute(
    onMenuClick: (() -> Unit)?,
    onOpenAi: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val pickScanFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        viewModel.onIntent(SettingsIntent.FolderPicked(FolderPurpose.Scan, uri?.toString()))
    }
    val pickExcludedFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        viewModel.onIntent(SettingsIntent.FolderPicked(FolderPurpose.Exclude, uri?.toString()))
    }

    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            is SettingsEffect.PickFolder -> when (effect.purpose) {
                FolderPurpose.Scan -> pickScanFolder.launch(null)
                FolderPurpose.Exclude -> pickExcludedFolder.launch(null)
            }
            is SettingsEffect.ShowMessage -> scope.launch { snackbarHostState.showSnackbar(effect.message.asString(context)) }
        }
    }

    SettingsScreen(
        state = state,
        onIntent = viewModel::onIntent,
        snackbarHostState = snackbarHostState,
        onMenuClick = onMenuClick,
        onOpenAi = onOpenAi,
    )
}
