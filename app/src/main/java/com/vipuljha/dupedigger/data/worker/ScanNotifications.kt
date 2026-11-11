package com.vipuljha.dupedigger.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.domain.model.ScanProgress
import com.vipuljha.dupedigger.domain.model.ScanStage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.NumberFormat
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** The ongoing scan notification: stage, count, a progress bar, Cancel, and Skip AI while the AI stage runs. */
@Singleton
class ScanNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun foregroundInfo(workId: UUID, progress: ScanProgress?): ForegroundInfo {
        ensureChannel()
        val count = NumberFormat.getIntegerInstance()
        val (title, text) = when (progress?.stage) {
            null -> context.getString(R.string.scan_notification_starting) to null
            ScanStage.INDEXING -> context.getString(R.string.scan_stage_indexing) to
                    context.resources.getQuantityString(
                        R.plurals.scan_files_found,
                        progress.done,
                        count.format(progress.done)
                    )

            ScanStage.EXACT -> context.getString(R.string.scan_stage_exact) to
                    progress.total?.takeIf { it > 0 }?.let {
                        context.getString(
                            R.string.scan_compared,
                            count.format(progress.done),
                            count.format(it)
                        )
                    }

            ScanStage.NEAR -> context.getString(R.string.scan_stage_near) to
                    progress.total?.takeIf { it > 0 }?.let {
                        context.getString(
                            R.string.scan_compared,
                            count.format(progress.done),
                            count.format(it)
                        )
                    }

            ScanStage.SEMANTIC -> context.getString(R.string.scan_stage_semantic) to
                    progress.total?.takeIf { it > 0 }?.let {
                        context.getString(
                            R.string.scan_compared,
                            count.format(progress.done),
                            count.format(it)
                        )
                    }
        }
        val total = progress?.total?.takeIf { it > 0 }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_scan)
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(total ?: 0, progress?.done?.coerceAtMost(total ?: 0) ?: 0, total == null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp())
            .addAction(
                0,
                context.getString(R.string.scan_action_cancel),
                WorkManager.getInstance(context).createCancelPendingIntent(workId)
            )
        if (progress?.stage == ScanStage.SEMANTIC) builder.addAction(
            0,
            context.getString(R.string.scan_action_skip_ai),
            skipAi()
        )
        return ForegroundInfo(
            NOTIFICATION_ID,
            builder.build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.scan_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
            .apply { description = context.getString(R.string.scan_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun skipAi(): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, SkipAiReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

    /** Brings back the open app, or starts it, on the Scan screen. */
    private fun openApp(): PendingIntent? =
        context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            it.action = ACTION_OPEN_SCAN
            it.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            PendingIntent.getActivity(
                context,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }

    companion object {
        const val ACTION_OPEN_SCAN = "com.vipuljha.dupedigger.action.OPEN_SCAN"

        private const val CHANNEL_ID = "scan"
        private const val NOTIFICATION_ID = 1001
    }
}
