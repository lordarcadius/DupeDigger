package com.vipuljha.dupedigger.data.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vipuljha.dupedigger.domain.repository.SemanticStageSkip
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** The scan notification's Skip AI action. */
class SkipAiReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        EntryPointAccessors.fromApplication(context, SkipAiEntryPoint::class.java)
            .semanticStageSkip().request()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SkipAiEntryPoint {
        fun semanticStageSkip(): SemanticStageSkip
    }
}
