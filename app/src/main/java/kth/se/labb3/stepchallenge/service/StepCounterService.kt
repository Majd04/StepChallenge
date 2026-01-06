package kth.se.labb3.stepchallenge.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kth.se.labb3.stepchallenge.MainActivity
import kth.se.labb3.stepchallenge.StepChallengeApplication
import kth.se.labb3.stepchallenge.data.dao.StepChallengeDatabase
import kth.se.labb3.stepchallenge.data.model.LeaderboardPeriod
import kth.se.labb3.stepchallenge.data.repository.LeaderboardRepository
import kth.se.labb3.stepchallenge.data.repository.StepRepository
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * Foreground service for continuous step tracking in the background.
 *
 * This service:
 * - Syncs step data from Health Connect every 5 minutes
 * - Updates Firebase with current step counts
 * - Checks if daily goal is reached and sends notifications
 * - Monitors leaderboard position changes
 * - Sends evening reminders if goal not reached
 */
class StepCounterService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = false

    private lateinit var stepRepository: StepRepository
    private lateinit var leaderboardRepository: LeaderboardRepository
    private lateinit var prefs: SharedPreferences

    private var currentUserId: String? = null
    private var dailyGoal: Int = 10000
    private var lastKnownRank: Int = 0
    private var goalReachedNotifiedToday: Boolean = false
    private var eveningReminderSentToday: Boolean = false

    companion object {
        private const val TAG = "StepCounterService"
        private const val NOTIFICATION_ID = 1001
        private const val SYNC_INTERVAL_MS = 1 * 60 * 1000L // 1 minute
        private const val PREFS_NAME = "step_counter_prefs"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_DAILY_GOAL = "daily_goal"
        private const val KEY_LAST_RANK = "last_rank"
        private const val KEY_GOAL_NOTIFIED_DATE = "goal_notified_date"
        private const val KEY_REMINDER_SENT_DATE = "reminder_sent_date"

        /**
         * Start the step counter service.
         */
        fun start(context: Context, userId: String? = null, dailyGoal: Int = 10000) {
            val intent = Intent(context, StepCounterService::class.java).apply {
                putExtra(KEY_USER_ID, userId)
                putExtra(KEY_DAILY_GOAL, dailyGoal)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stop the step counter service.
         */
        fun stop(context: Context) {
            val intent = Intent(context, StepCounterService::class.java)
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize repositories
        val database = StepChallengeDatabase.getDatabase(applicationContext)
        stepRepository = StepRepository(applicationContext, database.stepDataDao())
        leaderboardRepository = LeaderboardRepository()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // Load saved state
        currentUserId = prefs.getString(KEY_USER_ID, null)
        dailyGoal = prefs.getInt(KEY_DAILY_GOAL, 10000)
        lastKnownRank = prefs.getInt(KEY_LAST_RANK, 0)

        // Check if we already sent notifications today
        val today = LocalDate.now().toString()
        goalReachedNotifiedToday = prefs.getString(KEY_GOAL_NOTIFIED_DATE, "") == today
        eveningReminderSentToday = prefs.getString(KEY_REMINDER_SENT_DATE, "") == today

        startForeground(NOTIFICATION_ID, createNotification(0))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Update user ID and goal from intent
        intent?.let {
            it.getStringExtra(KEY_USER_ID)?.let { userId ->
                currentUserId = userId
                prefs.edit().putString(KEY_USER_ID, userId).apply()
            }
            val goal = it.getIntExtra(KEY_DAILY_GOAL, -1)
            if (goal > 0) {
                dailyGoal = goal
                prefs.edit().putInt(KEY_DAILY_GOAL, goal).apply()
            }
        }

        if (!isRunning && currentUserId != null) {
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

    /**
     * Main tracking loop that runs every SYNC_INTERVAL_MS.
     */
    private fun startStepTracking() {
        serviceScope.launch {
            while (isRunning) {
                try {
                    syncAndCheckData()
                } catch (e: Exception) {
                    Log.e(TAG, "Error in step tracking loop", e)
                }
                delay(SYNC_INTERVAL_MS)
            }
        }
    }

    /**
     * Sync step data and check for notifications.
     */
    private suspend fun syncAndCheckData() {
        val userId = currentUserId ?: return

        // Check if Health Connect permissions are granted
        if (!stepRepository.hasAllPermissions()) {
            Log.d(TAG, "Health Connect permissions not granted")
            return
        }

        // Get today's steps from Health Connect
        val todaySteps = stepRepository.getTodayStepsFromHealthConnect()
        val todayDistance = stepRepository.getTodayDistanceFromHealthConnect()
        val todayCalories = stepRepository.getTodayCaloriesFromHealthConnect()

        Log.d(TAG, "Synced steps: $todaySteps")

        // Update notification with current steps
        updateNotification(todaySteps)

        // Save to local database
        val stepData = stepRepository.syncTodayData(userId, dailyGoal)

        // Sync to Firebase
        leaderboardRepository.syncStepData(stepData)

        // Calculate weekly and monthly totals
        val today = LocalDate.now()
        val startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val startOfMonth = today.withDayOfMonth(1)

        val weeklySteps = calculateTotalSteps(startOfWeek, today)
        val monthlySteps = calculateTotalSteps(startOfMonth, today)

        // Update Firebase with totals
        leaderboardRepository.updateUserStepCounts(
            userId = userId,
            weeklySteps = weeklySteps,
            monthlySteps = monthlySteps,
            totalSteps = monthlySteps
        )

        // Check goal and send notifications
        checkGoalAndNotify(todaySteps)

        // Check leaderboard position
        checkLeaderboardPosition(userId)

        // Check if it's evening and send reminder if needed
        checkEveningReminder(todaySteps)
    }

    /**
     * Check if user reached goal and send notification.
     */
    private fun checkGoalAndNotify(currentSteps: Long) {
        val today = LocalDate.now().toString()

        // Reset flags if it's a new day
        if (prefs.getString(KEY_GOAL_NOTIFIED_DATE, "") != today) {
            goalReachedNotifiedToday = false
            eveningReminderSentToday = false
        }

        // Check if goal reached
        if (currentSteps >= dailyGoal && !goalReachedNotifiedToday) {
            NotificationHelper.sendGoalReachedNotification(
                context = applicationContext,
                totalSteps = currentSteps,
                dailyGoal = dailyGoal
            )
            goalReachedNotifiedToday = true
            prefs.edit().putString(KEY_GOAL_NOTIFIED_DATE, today).apply()
        }
    }

    /**
     * Check leaderboard position and notify if changed.
     */
    private suspend fun checkLeaderboardPosition(userId: String) {
        try {
            val currentRank = leaderboardRepository.getUserRank(userId, LeaderboardPeriod.WEEKLY)

            if (lastKnownRank > 0 && currentRank > 0) {
                when {
                    // User was overtaken (rank number increased = worse position)
                    currentRank > lastKnownRank -> {
                        val stepsToReclaim = leaderboardRepository.getStepsNeededForRank(
                            targetRank = lastKnownRank,
                            period = LeaderboardPeriod.WEEKLY,
                            currentUserId = userId
                        ) ?: 0L

                        NotificationHelper.sendOvertakenNotification(
                            context = applicationContext,
                            competitorName = "Someone",
                            newRank = currentRank,
                            stepsToReclaim = stepsToReclaim
                        )
                    }
                    // User moved up (rank number decreased = better position)
                    currentRank < lastKnownRank -> {
                        NotificationHelper.sendRankUpNotification(
                            context = applicationContext,
                            newRank = currentRank,
                            previousRank = lastKnownRank
                        )
                    }
                }
            }

            // Save current rank
            lastKnownRank = currentRank
            prefs.edit().putInt(KEY_LAST_RANK, currentRank).apply()

        } catch (e: Exception) {
            Log.e(TAG, "Error checking leaderboard position", e)
        }
    }

    /**
     * Send evening reminder if goal not reached.
     * Triggers around 8 PM.
     */
    private fun checkEveningReminder(currentSteps: Long) {
        val currentTime = LocalTime.now()
        val today = LocalDate.now().toString()

        // Reset flag if new day
        if (prefs.getString(KEY_REMINDER_SENT_DATE, "") != today) {
            eveningReminderSentToday = false
        }

        // Check if it's between 8 PM and 9 PM
        val isEveningTime = currentTime.hour == 20

        // Send reminder if:
        // - It's evening
        // - Goal not reached
        // - Haven't sent reminder today
        if (isEveningTime && currentSteps < dailyGoal && !eveningReminderSentToday) {
            NotificationHelper.sendGoalReminderNotification(
                context = applicationContext,
                currentSteps = currentSteps,
                dailyGoal = dailyGoal
            )
            eveningReminderSentToday = true
            prefs.edit().putString(KEY_REMINDER_SENT_DATE, today).apply()
        }
    }

    /**
     * Calculate total steps for a date range.
     */
    private suspend fun calculateTotalSteps(startDate: LocalDate, endDate: LocalDate): Long {
        val stepsMap = stepRepository.getStepsFromHealthConnect(startDate, endDate)
        return stepsMap.values.sum()
    }

    /**
     * Create the foreground service notification.
     */
    private fun createNotification(steps: Long): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stepsText = if (steps > 0) {
            "Today: ${java.text.NumberFormat.getNumberInstance().format(steps)} steps"
        } else {
            "Tracking your steps..."
        }

        return NotificationCompat.Builder(this, StepChallengeApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Step Challenge")
            .setContentText(stepsText)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    /**
     * Update the foreground notification with current step count.
     */
    private fun updateNotification(steps: Long) {
        val notification = createNotification(steps)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}