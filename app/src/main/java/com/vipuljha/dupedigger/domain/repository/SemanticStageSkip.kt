package com.vipuljha.dupedigger.domain.repository

import kotlinx.coroutines.flow.StateFlow

/** Lets the user skip the AI stage of the running scan without cancelling the scan. */
interface SemanticStageSkip {
    /** True once a skip is asked for, until the next scan starts. */
    val requested: StateFlow<Boolean>

    fun request()

    fun reset()
}
