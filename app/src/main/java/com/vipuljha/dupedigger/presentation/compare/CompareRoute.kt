package com.vipuljha.dupedigger.presentation.compare

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun compareViewModel(fingerprint: String, leftId: Long, rightId: Long): CompareViewModel =
    hiltViewModel<CompareViewModel, CompareViewModel.Factory>(creationCallback = { it.create(fingerprint, leftId, rightId) })

@Composable
fun CompareRoute(onBack: () -> Unit, viewModel: CompareViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CompareScreen(state = state, onBack = onBack)
}
