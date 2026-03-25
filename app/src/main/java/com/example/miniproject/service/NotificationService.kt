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
