import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import se.kth.stepchallenge.MainActivity
import se.kth.stepchallenge.StepChallengeApplication

/**
 * Foreground service for continuous step tracking.
 * This service runs in the background and periodically syncs step data.
 */
class StepCounterService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isRunning = false

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val SYNC_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes

        fun start(context: Context) {
            val intent = Intent(context, StepCounterService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, StepCounterService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            startStepTracking()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
    }

    private fun startStepTracking() {
        serviceScope.launch {
            while (isRunning) {
                // Sync step data periodically
                syncStepData()
                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    private suspend fun syncStepData() {
        // This would typically use the StepRepository to sync data
        // For now, this is a placeholder
        try {
            // TODO: Inject StepRepository and sync data
            // stepRepository.syncTodayData(userId, dailyGoal)
        } catch (e: Exception) {
            // Log error but don't crash
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, StepChallengeApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Step Challenge")
            .setContentText("Tracking your steps...")
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Replace with your app icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}