package com.vipuljha.dupedigger.data.worker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.domain.model.ScanProgress
import com.vipuljha.dupedigger.domain.model.ScanStage
import dagger.hilt.android.EntryPointAccessors
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** The notification's Skip AI action, sent as the system would send it, reaches the app's skip signal. */
@RunWith(AndroidJUnit4::class)
class SkipAiActionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val skip = EntryPointAccessors.fromApplication(context, SkipAiReceiver.SkipAiEntryPoint::class.java).semanticStageSkip()
    private val notifications = ScanNotifications(context)

    @After
    fun tearDown() = skip.reset()

    private fun skipAction(stage: ScanStage) = notifications.foregroundInfo(UUID.randomUUID(), ScanProgress(stage, 1, 10))
        .notification.actions.orEmpty().firstOrNull { it.title == context.getString(R.string.scan_action_skip_ai) }

    @Test
    fun skipAiIsOfferedOnlyDuringTheAiStage() {
        assertNull(skipAction(ScanStage.NEAR))
        assertTrue(skipAction(ScanStage.SEMANTIC) != null)
    }

    @Test
    fun tappingSkipAiAsksTheRunningScanToSkipIt() {
        skip.reset()

        skipAction(ScanStage.SEMANTIC)!!.actionIntent.send()

        val deadline = System.currentTimeMillis() + TIMEOUT_MS
        while (!skip.requested.value && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MS)
        assertTrue(skip.requested.value)
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
        const val POLL_MS = 50L
    }
}
