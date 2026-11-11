package com.vipuljha.dupedigger.presentation.compare

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vipuljha.dupedigger.core.mvi.ObserveAsEvents
import com.vipuljha.dupedigger.presentation.common.rememberRemovalEventHandler

@Composable
fun compareViewModel(fingerprint: String, leftId: Long, rightId: Long): CompareViewModel =
    hiltViewModel<CompareViewModel, CompareViewModel.Factory>(creationCallback = {
        it.create(
            fingerprint,
            leftId,
            rightId
        )
    })

@Composable
fun CompareRoute(onBack: () -> Unit, viewModel: CompareViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val onRemoval = rememberRemovalEventHandler(
        snackbarHostState,
        onTrashAnswered = { viewModel.onIntent(CompareIntent.TrashAnswered(it)) },
        onUndo = { viewModel.onIntent(CompareIntent.UndoClicked(it)) },
    )
    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            is CompareEffect.Removal -> onRemoval(effect.event)
        }
    }
    CompareScreen(
        state = state,
        onIntent = viewModel::onIntent,
        snackbarHostState = snackbarHostState,
        onBack = onBack
    )
}
