package kth.se.labb3.stepchallenge.service

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kth.se.labb3.stepchallenge.MainActivity
import kth.se.labb3.stepchallenge.R
import kth.se.labb3.stepchallenge.StepChallengeApplication

/**
 * Helper class for managing all app notifications.
 * Handles goal reminders, leaderboard updates, and achievement notifications.
 */
object NotificationHelper {

    private const val NOTIFICATION_ID_GOAL_REMINDER = 2001
    private const val NOTIFICATION_ID_GOAL_REACHED = 2002
    private const val NOTIFICATION_ID_LEADERBOARD = 2003
    private const val NOTIFICATION_ID_OVERTAKEN = 2004

    /**
     * Check if notification permission is granted (required for Android 13+)
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Send a reminder notification when user hasn't reached daily goal.
     * Called in the evening (e.g., 8 PM) if steps < goal.
     */
    fun sendGoalReminderNotification(
        context: Context,
        currentSteps: Long,
        dailyGoal: Int
    ) {
        if (!hasNotificationPermission(context)) return

        val stepsRemaining = dailyGoal - currentSteps
        val percentComplete = ((currentSteps.toFloat() / dailyGoal) * 100).toInt()

        val notification = NotificationCompat.Builder(
            context,
            StepChallengeApplication.NOTIFICATION_CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🚶 Keep going! You're at $percentComplete%")
            .setContentText("Only ${formatNumber(stepsRemaining)} more steps to reach your daily goal!")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("You've walked ${formatNumber(currentSteps)} steps today. Just ${formatNumber(stepsRemaining)} more to hit your ${formatNumber(dailyGoal.toLong())} step goal! Get moving! 💪")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(createMainActivityIntent(context))
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_GOAL_REMINDER, notification)
    }

    /**
     * Send notification when user reaches their daily goal.
     */
    fun sendGoalReachedNotification(
        context: Context,
        totalSteps: Long,
        dailyGoal: Int
    ) {
        if (!hasNotificationPermission(context)) return

        val notification = NotificationCompat.Builder(
            context,
            StepChallengeApplication.NOTIFICATION_CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🎉 Daily Goal Reached!")
            .setContentText("Congratulations! You've walked ${formatNumber(totalSteps)} steps today!")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Amazing work! You crushed your ${formatNumber(dailyGoal.toLong())} step goal with ${formatNumber(totalSteps)} steps! Keep up the great work! 🏆")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(createMainActivityIntent(context))
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_GOAL_REACHED, notification)
    }

    /**
     * Send notification when someone overtakes the user on the leaderboard.
     */
    fun sendOvertakenNotification(
        context: Context,
        competitorName: String,
        newRank: Int,
        stepsToReclaim: Long
    ) {
        if (!hasNotificationPermission(context)) return

        val notification = NotificationCompat.Builder(
            context,
            StepChallengeApplication.NOTIFICATION_CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📉 You've been overtaken!")
            .setContentText("$competitorName passed you! You're now #$newRank")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$competitorName just passed you on the leaderboard! You're now at rank #$newRank. Walk ${formatNumber(stepsToReclaim)} more steps to reclaim your position! 🏃")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(createMainActivityIntent(context))
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_OVERTAKEN, notification)
    }

    /**
     * Send notification when user moves up on the leaderboard.
     */
    fun sendRankUpNotification(
        context: Context,
        newRank: Int,
        previousRank: Int
    ) {
        if (!hasNotificationPermission(context)) return

        val notification = NotificationCompat.Builder(
            context,
            StepChallengeApplication.NOTIFICATION_CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("📈 You moved up!")
            .setContentText("You're now #$newRank on the leaderboard!")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Great job! You moved from #$previousRank to #$newRank on the leaderboard! Keep walking to climb even higher! 🚀")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(createMainActivityIntent(context))
            .setAutoCancel(true)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID_LEADERBOARD, notification)
    }

    /**
     * Create pending intent to open MainActivity.
     */
    private fun createMainActivityIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /**
     * Format number with thousand separators.
     */
    private fun formatNumber(number: Long): String {
        return java.text.NumberFormat.getNumberInstance().format(number)
    }
}