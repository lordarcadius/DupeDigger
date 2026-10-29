package com.vipuljha.dupedigger.presentation.group

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.mvi.ObserveAsEvents
import com.vipuljha.dupedigger.core.ui.asString
import kotlinx.coroutines.launch

@Composable
fun groupViewModel(fingerprint: String): GroupViewModel =
    hiltViewModel<GroupViewModel, GroupViewModel.Factory>(creationCallback = { factory -> factory.create(fingerprint) })

@Composable
fun GroupRoute(
    onBack: () -> Unit,
    onCompare: (keeperId: Long, otherId: Long) -> Unit,
    viewModel: GroupViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            is GroupEffect.OpenFile -> {
                val intent = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(Uri.parse(effect.uri), effect.mimeType ?: "*/*")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try {
                    context.startActivity(intent)
                } catch (_: ActivityNotFoundException) {
                    scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.group_open_no_app)) }
                }
            }
            is GroupEffect.ShowMessage -> scope.launch { snackbarHostState.showSnackbar(effect.message.asString(context)) }
            GroupEffect.Close -> onBack()
            is GroupEffect.OpenCompare -> onCompare(effect.keeperId, effect.otherId)
        }
    }

    GroupScreen(state = state, onIntent = viewModel::onIntent, snackbarHostState = snackbarHostState, onBack = onBack)
}
