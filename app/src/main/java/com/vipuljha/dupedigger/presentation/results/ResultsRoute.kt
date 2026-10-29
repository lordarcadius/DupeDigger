package com.vipuljha.dupedigger.presentation.results

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.vipuljha.dupedigger.core.mvi.ObserveAsEvents

@Composable
fun ResultsRoute(
    onMenuClick: (() -> Unit)?,
    onStartScan: () -> Unit,
    viewModel: ResultsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val groups = viewModel.groups.collectAsLazyPagingItems()

    ObserveAsEvents(viewModel.effect) { effect ->
        when (effect) {
            ResultsEffect.OpenScan -> onStartScan()
        }
    }

    ResultsScreen(state = state, groups = groups, onIntent = viewModel::onIntent, onMenuClick = onMenuClick)
}
