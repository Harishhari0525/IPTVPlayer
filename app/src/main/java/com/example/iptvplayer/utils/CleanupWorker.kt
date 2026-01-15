package com.example.iptvplayer.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import android.content.pm.ServiceInfo
import com.example.iptvplayer.data.repository.ChannelRepository

class CleanupWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    // Initialize Repository manually since we aren't using Hilt/Dagger
    private val repository = ChannelRepository(context)
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    override suspend fun doWork(): Result {
        val allChannels = repository.getChannelsSync() // We will add this helper to Repo
        val total = allChannels.size
        var deletedCount = 0

        // Create Notification Channel for Android O+
        createNotificationChannel()

        // Loop through all channels
        for ((index, channel) in allChannels.withIndex()) {

            // 1. Update Notification every 10 items (to avoid spamming system)
            if (index % 10 == 0) {
                setForeground(createForegroundInfo(index + 1, total, deletedCount))
                setProgress(workDataOf("progress" to index, "total" to total))
            }

            // 2. Check if Alive
            val isAlive = repository.isChannelAlive(channel.url)

            if (!isAlive) {
                repository.deleteChannel(channel.id)
                deletedCount++
            }
        }

        // Show final success notification
        showResultNotification(deletedCount)

        return Result.success()
    }

    private fun createForegroundInfo(current: Int, total: Int, deleted: Int): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, "cleanup_channel")
            .setContentTitle("Cleaning Playlist")
            .setContentText("Checking $current / $total (Removed: $deleted)")
            .setSmallIcon(android.R.drawable.ic_menu_delete)
            .setOngoing(true)
            .setProgress(total, current, false)
            .build()
        return ForegroundInfo(
            101,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }

    private fun showResultNotification(deletedCount: Int) {
        val notification = NotificationCompat.Builder(applicationContext, "cleanup_channel")
            .setContentTitle("Cleanup Complete")
            .setContentText("Removed $deletedCount dead channels.")
            .setSmallIcon(android.R.drawable.ic_menu_delete)
            .setOngoing(false)
            .build()
        notificationManager.notify(102, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "cleanup_channel",
            "Playlist Cleanup",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(channel)
    }
}