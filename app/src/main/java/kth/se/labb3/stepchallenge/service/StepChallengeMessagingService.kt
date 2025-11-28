package kth.se.labb3.stepchallenge.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kth.se.labb3.stepchallenge.MainActivity
import kth.se.labb3.stepchallenge.StepChallengeApplication

/**
 * Firebase Cloud Messaging service for handling push notifications.
 */
class StepChallengeMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM token: $token")
        // TODO: Send token to your server for targeted notifications
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        Log.d(TAG, "Message received from: ${message.from}")

        message.notification?.let { notification ->
            showNotification(
                title = notification.title ?: "Step Challenge",
                body = notification.body ?: ""
            )
        }

        if (message.data.isNotEmpty()) {
            handleDataMessage(message.data)
        }
    }

    private fun showNotification(title: String, body: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(
            this,
            StepChallengeApplication.NOTIFICATION_CHANNEL_ID
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun handleDataMessage(data: Map<String, String>) {
        when (data["type"]) {
            "leaderboard_update" -> {
                val rank = data["rank"]
                showNotification(
                    title = "Leaderboard Update!",
                    body = "Your new rank is #$rank"
                )
            }
            "goal_reached" -> {
                showNotification(
                    title = "Goal Reached! 🎉",
                    body = "Congratulations! You've reached your daily step goal!"
                )
            }
            "challenge" -> {
                val challenger = data["challenger"]
                showNotification(
                    title = "New Challenge!",
                    body = "$challenger has challenged you to a step competition!"
                )
            }
        }
    }
}