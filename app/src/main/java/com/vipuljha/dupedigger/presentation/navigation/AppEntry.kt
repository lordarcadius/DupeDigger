package com.vipuljha.dupedigger.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.vipuljha.dupedigger.domain.repository.SettingsRepository
import com.vipuljha.dupedigger.presentation.onboarding.OnboardingRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppEntryViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    /** Null until the stored flag is read, so neither screen flashes first. */
    val onboardingCompleted: StateFlow<Boolean?> = settings.settings
        .map { it.onboardingCompleted }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** Shows onboarding once, then the app shell. */
@Composable
fun AppEntry(shortcuts: AppShortcuts, viewModel: AppEntryViewModel = hiltViewModel()) {
    val completed by viewModel.onboardingCompleted.collectAsStateWithLifecycle()
    when (completed) {
        null -> Unit
        false -> OnboardingRoute()
        true -> AppRoot(shortcuts)
    }
}
