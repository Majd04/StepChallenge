package se.kth.stepchallenge.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import se.kth.stepchallenge.data.local.StepChallengeDatabase
import se.kth.stepchallenge.data.model.DailyStepSummary
import se.kth.stepchallenge.data.model.StepData
import se.kth.stepchallenge.data.repository.LeaderboardRepository
import se.kth.stepchallenge.data.repository.StepRepository
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
    val lastSyncTime: Long = 0
)

/**
 * ViewModel for step tracking and data management.
 */
class StepViewModel(application: Application) : AndroidViewModel(application) {

    private val database = StepChallengeDatabase.getDatabase(application)
    private val stepRepository = StepRepository(application, database.stepDataDao())
    private val leaderboardRepository = LeaderboardRepository()

    private val _uiState = MutableStateFlow(StepUiState())
    val uiState: StateFlow<StepUiState> = _uiState.asStateFlow()

    private var currentUserId: String? = null

    init {
        checkHealthConnectAvailability()
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
                val weeklySteps = calculateWeeklySteps(userId, startOfWeek, today)

                // Calculate monthly steps
                val startOfMonth = today.withDayOfMonth(1)
                val monthlySteps = calculateMonthlySteps(userId, startOfMonth, today)

                // Get weekly data for chart
                val weeklyData = getWeeklyChartData(userId, startOfWeek, today)

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

                val weeklySteps = stepRepository.getTotalStepsForRange(userId, startOfWeek, today) + stepData.steps
                val monthlySteps = stepRepository.getTotalStepsForRange(userId, startOfMonth, today) + stepData.steps

                leaderboardRepository.updateUserStepCounts(
                    userId = userId,
                    weeklySteps = weeklySteps,
                    monthlySteps = monthlySteps,
                    totalSteps = monthlySteps // Simplified, should track all-time
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
                delay(60_000) // 1 minute
                if (_uiState.value.hasHealthConnectPermission) {
                    syncStepData()
                }
            }
        }
    }

    /**
     * Update daily goal.
     */
    fun updateDailyGoal(goal: Int) {
        _uiState.value = _uiState.value.copy(
            dailyGoal = goal,
            goalProgress = if (goal > 0) {
                (_uiState.value.todaySteps.toFloat() / goal).coerceAtMost(1f)
            } else 0f
        )
    }

    /**
     * Clear error message.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private suspend fun calculateWeeklySteps(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Long {
        val healthConnectSteps = stepRepository.getStepsFromHealthConnect(startDate, endDate)
        return healthConnectSteps.values.sum()
    }

    private suspend fun calculateMonthlySteps(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Long {
        val healthConnectSteps = stepRepository.getStepsFromHealthConnect(startDate, endDate)
        return healthConnectSteps.values.sum()
    }

    private suspend fun getWeeklyChartData(
        userId: String,
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