package com.vipuljha.dupedigger.data.worker

import com.vipuljha.dupedigger.domain.repository.SemanticStageSkip
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Held in memory: the scan worker, the Scan screen and the notification action all run in the app's one process. */
@Singleton
class InMemorySemanticStageSkip @Inject constructor() : SemanticStageSkip {
    private val state = MutableStateFlow(false)

    override val requested: StateFlow<Boolean> = state.asStateFlow()

    override fun request() {
        state.value = true
    }

    override fun reset() {
        state.value = false
    }
}
