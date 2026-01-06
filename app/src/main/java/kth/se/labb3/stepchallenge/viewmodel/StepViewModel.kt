package kth.se.labb3.stepchallenge.viewmodel

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kth.se.labb3.stepchallenge.data.dao.StepChallengeDatabase
import kth.se.labb3.stepchallenge.data.model.DailyStepSummary
import kth.se.labb3.stepchallenge.data.repository.LeaderboardRepository
import kth.se.labb3.stepchallenge.data.repository.StepRepository
import kth.se.labb3.stepchallenge.service.StepCounterService
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * UI state for step tracking.
 */
data class StepUiState(
    val isLoading: Boolean = false,
    val todaySteps: Long = 0,
    val todayDistance: Double = 0.0,
    val todayCalories: Double = 0.0,
    val dailyGoal: Int = 10000,
    val goalProgress: Float = 0f,
    val weeklySteps: Long = 0,
    val monthlySteps: Long = 0,
    val weeklyData: List<DailyStepSummary> = emptyList(),
    val hasHealthConnectPermission: Boolean = false,
    val isHealthConnectAvailable: Boolean = false,
    val error: String? = null,
    val lastSyncTime: Long = 0,
    val goalSaved: Boolean = false  // For showing save confirmation
)

/**
 * ViewModel for step tracking and data management.
 */
class StepViewModel(application: Application) : AndroidViewModel(application) {

    private val database = StepChallengeDatabase.getDatabase(application)
    private val stepRepository = StepRepository(application, database.stepDataDao())
    private val leaderboardRepository = LeaderboardRepository()

    // SharedPreferences for persistent storage
    private val prefs: SharedPreferences = application.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    private val _uiState = MutableStateFlow(StepUiState())
    val uiState: StateFlow<StepUiState> = _uiState.asStateFlow()

    private var currentUserId: String? = null

    companion object {
        private const val PREFS_NAME = "step_challenge_settings"
        private const val KEY_DAILY_GOAL = "daily_goal"
        private const val DEFAULT_DAILY_GOAL = 10000
    }

    init {
        checkHealthConnectAvailability()
        // Load saved daily goal from SharedPreferences
        loadSavedDailyGoal()
    }

    /**
     * Load saved daily goal from SharedPreferences.
     */
    private fun loadSavedDailyGoal() {
        val savedGoal = prefs.getInt(KEY_DAILY_GOAL, DEFAULT_DAILY_GOAL)
        _uiState.value = _uiState.value.copy(dailyGoal = savedGoal)
    }

    /**
     * Set the current user ID for step tracking.
     */
    fun setUserId(userId: String) {
        currentUserId = userId
        loadStepData()
    }

    /**
     * Check if Health Connect is available on the device.
     */
    private fun checkHealthConnectAvailability() {
        val isAvailable = stepRepository.isHealthConnectAvailable()
        _uiState.value = _uiState.value.copy(isHealthConnectAvailable = isAvailable)
    }

    /**
     * Check and update Health Connect permission status.
     */
    fun checkPermissions() {
        viewModelScope.launch {
            val hasPermission = stepRepository.hasAllPermissions()
            _uiState.value = _uiState.value.copy(hasHealthConnectPermission = hasPermission)

            if (hasPermission) {
                syncStepData()
            }
        }
    }

    /**
     * Load step data from local database and Health Connect.
     */
    private fun loadStepData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val userId = currentUserId ?: return@launch

            try {
                // Get today's data
                val todaySteps = stepRepository.getTodayStepsFromHealthConnect()
                val todayDistance = stepRepository.getTodayDistanceFromHealthConnect()
                val todayCalories = stepRepository.getTodayCaloriesFromHealthConnect()

                // Calculate weekly steps
                val today = LocalDate.now()
                val startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val weeklySteps = calculateWeeklySteps(startOfWeek, today)

                // Calculate monthly steps
                val startOfMonth = today.withDayOfMonth(1)
                val monthlySteps = calculateMonthlySteps(startOfMonth, today)

                // Get weekly data for chart
                val weeklyData = getWeeklyChartData(startOfWeek, today)

                val dailyGoal = _uiState.value.dailyGoal
                val goalProgress = if (dailyGoal > 0) {
                    (todaySteps.toFloat() / dailyGoal).coerceAtMost(1f)
                } else 0f

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    todaySteps = todaySteps,
                    todayDistance = todayDistance,
                    todayCalories = todayCalories,
                    goalProgress = goalProgress,
                    weeklySteps = weeklySteps,
                    monthlySteps = monthlySteps,
                    weeklyData = weeklyData,
                    lastSyncTime = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    /**
     * Sync step data from Health Connect and update Firebase.
     */
    fun syncStepData() {
        viewModelScope.launch {
            val userId = currentUserId ?: return@launch
            _uiState.value = _uiState.value.copy(isLoading = true)

            try {
                // Sync today's data from Health Connect
                val stepData = stepRepository.syncTodayData(userId, _uiState.value.dailyGoal)

                // Sync to Firebase
                leaderboardRepository.syncStepData(stepData)

                // Update weekly/monthly totals in Firebase
                val today = LocalDate.now()
                val startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                val startOfMonth = today.withDayOfMonth(1)

                val weeklySteps = calculateWeeklySteps(startOfWeek, today)
                val monthlySteps = calculateMonthlySteps(startOfMonth, today)

                leaderboardRepository.updateUserStepCounts(
                    userId = userId,
                    weeklySteps = weeklySteps,
                    monthlySteps = monthlySteps,
                    totalSteps = monthlySteps
                )

                // Reload data
                loadStepData()

                // Mark as synced
                stepRepository.markAsSynced(stepData.id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    /**
     * Start auto-sync every minute.
     */
    fun startAutoSync() {
        viewModelScope.launch {
            while (true) {
                delay(60_000)
                if (_uiState.value.hasHealthConnectPermission) {
                    syncStepData()
                }
            }
        }
    }

    /**
     * Update daily goal and save to SharedPreferences.
     */
    fun updateDailyGoal(goal: Int) {
        // Save to SharedPreferences (persistent)
        prefs.edit().putInt(KEY_DAILY_GOAL, goal).apply()

        // Update UI state
        _uiState.value = _uiState.value.copy(
            dailyGoal = goal,
            goalProgress = if (goal > 0) {
                (_uiState.value.todaySteps.toFloat() / goal).coerceAtMost(1f)
            } else 0f,
            goalSaved = true
        )

        // Update background service with new goal
        val context = getApplication<Application>()
        currentUserId?.let { userId ->
            StepCounterService.start(context, userId, goal)
        }

        // Reset goalSaved flag after a delay
        viewModelScope.launch {
            delay(2000)
            _uiState.value = _uiState.value.copy(goalSaved = false)
        }
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private suspend fun calculateWeeklySteps(
        startDate: LocalDate,
        endDate: LocalDate
    ): Long {
        val healthConnectSteps = stepRepository.getStepsFromHealthConnect(startDate, endDate)
        return healthConnectSteps.values.sum()
    }

    private suspend fun calculateMonthlySteps(
        startDate: LocalDate,
        endDate: LocalDate
    ): Long {
        val healthConnectSteps = stepRepository.getStepsFromHealthConnect(startDate, endDate)
        return healthConnectSteps.values.sum()
    }

    private suspend fun getWeeklyChartData(
        startDate: LocalDate,
        endDate: LocalDate
    ): List<DailyStepSummary> {
        val healthConnectSteps = stepRepository.getStepsFromHealthConnect(startDate, endDate)
        val dailyGoal = _uiState.value.dailyGoal

        return (0..6).map { dayOffset ->
            val date = startDate.plusDays(dayOffset.toLong())
            val steps = healthConnectSteps[date] ?: 0L
            DailyStepSummary(
                date = date,
                steps = steps,
                goal = dailyGoal
            )
        }
    }

    /**
     * Get required Health Connect permissions.
     */
    fun getRequiredPermissions() = StepRepository.PERMISSIONS
}