package com.vipuljha.dupedigger.domain.usecase

import com.vipuljha.dupedigger.domain.repository.SettingsRepository
import javax.inject.Inject

class CompleteOnboardingUseCase @Inject constructor(
    private val settings: SettingsRepository,
) {
    suspend operator fun invoke() = settings.update { it.copy(onboardingCompleted = true) }
}
