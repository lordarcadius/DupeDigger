package com.vipuljha.dupedigger.presentation.results

import android.app.Activity
import android.content.IntentSender
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.mvi.ObserveAsEvents
import com.vipuljha.dupedigger.core.ui.asString
import kotlinx.coroutines.launch

@Composable
fun ResultsRoute(
    onMenuClick: (() -> Unit)?,
    onOpenGroup: (fingerprint: String) -> Unit,
    onStartScan: () -> Unit,
    viewModel: ResultsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val groups = viewModel.groups.collectAsLazyPagingItems()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val trashPrompt = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onIntent(ResultsIntent.TrashAnswered(result.resultCode == Activity.RESULT_OK))
    }

    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            ResultsEffect.OpenScan -> onStartScan()
            is ResultsEffect.LaunchTrash -> trashPrompt.launch(IntentSenderRequest.Builder(effect.request as IntentSender).build())
            is ResultsEffect.ShowMessage -> scope.launch {
                val undoLabel = effect.undo?.let { context.getString(R.string.action_undo) }
                val answer = snackbarHostState.showSnackbar(effect.message.asString(context), actionLabel = undoLabel, duration = SnackbarDuration.Long)
                if (answer == SnackbarResult.ActionPerformed) effect.undo?.let { viewModel.onIntent(ResultsIntent.UndoClicked(it)) }
            }
        }
    }

    ResultsScreen(
        state = state,
        groups = groups,
        onIntent = viewModel::onIntent,
        onOpenGroup = onOpenGroup,
        snackbarHostState = snackbarHostState,
        onMenuClick = onMenuClick,
    )
}
