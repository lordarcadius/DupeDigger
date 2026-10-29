package com.vipuljha.dupedigger.presentation.ai

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
import com.vipuljha.dupedigger.core.ui.rememberNotificationPermissionGate
import kotlinx.coroutines.launch

@Composable
fun AiRoute(
    onBack: () -> Unit,
    viewModel: AiViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val withNotificationPermission = rememberNotificationPermissionGate()

    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            is AiEffect.ShowMessage -> scope.launch { snackbarHostState.showSnackbar(effect.message.asString(context)) }
        }
    }

    AiScreen(
        state = state,
        onIntent = { intent ->
            // The download shows progress in a notification, so ask for that permission first, as Scan does.
            if (intent is AiIntent.DownloadClicked) withNotificationPermission { viewModel.onIntent(intent) } else viewModel.onIntent(intent)
        },
        snackbarHostState = snackbarHostState,
        onBack = onBack,
    )
}
