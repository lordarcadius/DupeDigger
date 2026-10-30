package com.vipuljha.dupedigger.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import com.vipuljha.dupedigger.R
import com.vipuljha.dupedigger.core.ui.asString
import com.vipuljha.dupedigger.core.ui.formatBytes
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** The ongoing model download notification: bytes so far, a progress bar and Cancel. */
@Singleton
class ModelDownloadNotifications @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun foregroundInfo(workId: UUID, downloaded: Long, total: Long): ForegroundInfo {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.model_channel_name), NotificationManager.IMPORTANCE_LOW),
        )
        val percent = if (total > 0) (downloaded * 100 / total).toInt() else 0
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_scan)
            .setContentTitle(context.getString(R.string.model_notification_title))
            .setContentText(context.getString(R.string.model_notification_progress, formatBytes(downloaded).asString(context), formatBytes(total).asString(context)))
            .setProgress(100, percent, total <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, context.getString(R.string.action_cancel), WorkManager.getInstance(context).createCancelPendingIntent(workId))
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    }

    private companion object {
        const val CHANNEL_ID = "model_download"
        const val NOTIFICATION_ID = 1002
    }
}
