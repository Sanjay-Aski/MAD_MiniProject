package com.example.miniproject.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.graphics.BitmapFactory
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.example.miniproject.R
import com.example.miniproject.RunTrackingActivity

class NotificationService(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "running_tracker_channel"

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Running Tracker Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for running activities and milestones"
                enableVibration(true)
                setShowBadge(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showRunStartedNotification() {
        val intent = Intent(context, RunTrackingActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = baseBuilder(pendingIntent)
            .setContentTitle("Run Started")
            .setContentText("Tracking is live. Keep your pace steady.")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Tracking started successfully. GPS, steps, and calories are being synced.")
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(1, notification)
    }

    fun showRunEndedNotification(distance: Double, calories: Double) {
        val intent = Intent(context, RunTrackingActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = baseBuilder(pendingIntent)
            .setContentTitle("Congratulations!")
            .setContentText("Run completed! ${String.format("%.2f", distance)} km")
            .setStyle(
                NotificationCompat.InboxStyle()
                    .addLine("Distance: ${String.format("%.2f", distance)} km")
                    .addLine("Calories: ${String.format("%.0f", calories)} cal")
                    .setSummaryText("Nice work")
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(2, notification)
    }

    fun showDailyGoalAchievedNotification(goalType: String) {
        val notification = baseBuilder(null)
            .setContentTitle("Goal Achieved")
            .setContentText("You've reached your daily $goalType goal!")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(3, notification)
    }

    fun showHighScoreNotification(metric: String, value: String) {
        val notification = baseBuilder(null)
            .setContentTitle("New Personal Record!")
            .setContentText("Your new $metric: $value")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        notificationManager.notify(4, notification)
    }

    fun showPauseReminder() {
        val notification = baseBuilder(null)
            .setContentTitle("Take a Break")
            .setContentText("You've been running for a while. Consider taking a rest!")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        notificationManager.notify(5, notification)
    }

    /**
     * Show progress milestone notification (30%, 60%, 80%, 100%)
     */
    fun showProgressMilestoneNotification(progressPercent: Int, distance: Double, duration: Long) {
        val emoji = when (progressPercent) {
            30 -> "🔥"
            60 -> "💪"
            80 -> "⚡"
            100 -> "🎉"
            else -> "📍"
        }
        
        val message = when (progressPercent) {
            30 -> "Great start! You're 30% through your run."
            60 -> "Halfway done! Keep pushing, you're doing great!"
            80 -> "Almost there! 80% complete. Final push!"
            100 -> "Congratulations! Run completed successfully!"
            else -> "Run progress: $progressPercent%"
        }
        
        val notificationId = 200 + progressPercent // Unique IDs: 230, 260, 280, 2100
        
        val notification = baseBuilder(null)
            .setContentTitle("$emoji Run Progress - $progressPercent%")
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$message\n\nDistance: ${String.format("%.2f", distance)} km\nTime: ${formatDuration(duration)}")
            )
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVibrate(longArrayOf(0, 250, 250, 250)) // Haptic feedback
            .build()
        
        notificationManager.notify(notificationId, notification)
    }

    /**
     * Show achievement badges for hitting milestones during run
     */
    fun showAchievementNotification(title: String, description: String, emoji: String = "🏅") {
        val notification = baseBuilder(null)
            .setContentTitle("$emoji Achievement!")
            .setContentText(title)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(description)
            )
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVibrate(longArrayOf(0, 200, 100, 200)) // Double vibration
            .build()
        
        notificationManager.notify(300, notification)
    }

    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, secs)
    }

    private fun baseBuilder(contentIntent: PendingIntent?): NotificationCompat.Builder {
        val largeIcon = BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher)
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_nav_dashboard)
            .setLargeIcon(largeIcon)
            .setColor(ContextCompat.getColor(context, R.color.accent_blue))
            .setColorized(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setSubText("RunSense")
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .apply {
                if (contentIntent != null) {
                    setContentIntent(contentIntent)
                }
            }
    }
}
