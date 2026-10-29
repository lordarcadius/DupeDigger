package com.vipuljha.dupedigger.presentation.quarantine

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
fun QuarantineRoute(
    onMenuClick: (() -> Unit)?,
    viewModel: QuarantineViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            is QuarantineEffect.ShowMessage -> scope.launch { snackbarHostState.showSnackbar(effect.message.asString(context)) }
        }
    }

    QuarantineScreen(state = state, onIntent = viewModel::onIntent, snackbarHostState = snackbarHostState, onMenuClick = onMenuClick)
}
