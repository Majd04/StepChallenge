package kth.se.labb3.stepchallenge.data.repository

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kth.se.labb3.stepchallenge.data.dao.StepDataDao
import kth.se.labb3.stepchallenge.data.model.DailyStepSummary
import kth.se.labb3.stepchallenge.data.model.StepData
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Repository for managing step data from Health Connect and local database.
 */
class StepRepository(
    private val context: Context,
    private val stepDataDao: StepDataDao
) {
    private val healthConnectClient: HealthConnectClient? by lazy {
        try {
            HealthConnectClient.getOrCreate(context)
        } catch (e: Exception) {
            Log.e(TAG, "Health Connect not available", e)
            null
        }
    }

    companion object {
        private const val TAG = "StepRepository"

        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getWritePermission(StepsRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
        )
    }

    /**
     * Check if Health Connect is available on this device.
     */
    fun isHealthConnectAvailable(): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    /**
     * Check if all required permissions are granted.
     */
    suspend fun hasAllPermissions(): Boolean {
        val client = healthConnectClient ?: return false
        return try {
            val granted = client.permissionController.getGrantedPermissions()
            PERMISSIONS.all { it in granted }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking permissions", e)
            false
        }
    }

    /**
     * Get today's step data from Health Connect.
     */
    suspend fun getTodayStepsFromHealthConnect(): Long {
        val client = healthConnectClient ?: return 0L

        return try {
            val today = LocalDate.now()
            val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val now = Instant.now()

            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, now)
                )
            )

            response.records.sumOf { it.count }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading steps from Health Connect", e)
            0L
        }
    }

    /**
     * Get steps for a specific date range from Health Connect.
     */
    suspend fun getStepsFromHealthConnect(startDate: LocalDate, endDate: LocalDate): Map<LocalDate, Long> {
        val client = healthConnectClient ?: return emptyMap()

        return try {
            val startInstant = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val endInstant = endDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()

            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startInstant, endInstant)
                )
            )

            // Group steps by date
            response.records
                .groupBy { record ->
                    LocalDateTime.ofInstant(record.startTime, ZoneId.systemDefault()).toLocalDate()
                }
                .mapValues { (_, records) ->
                    records.sumOf { it.count }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading steps for range", e)
            emptyMap()
        }
    }

    /**
     * Get today's distance from Health Connect.
     */
    suspend fun getTodayDistanceFromHealthConnect(): Double {
        val client = healthConnectClient ?: return 0.0

        return try {
            val today = LocalDate.now()
            val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val now = Instant.now()

            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = DistanceRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, now)
                )
            )

            response.records.sumOf { it.distance.inMeters }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading distance", e)
            0.0
        }
    }

    /**
     * Get today's calories burned from Health Connect.
     */
    suspend fun getTodayCaloriesFromHealthConnect(): Double {
        val client = healthConnectClient ?: return 0.0

        return try {
            val today = LocalDate.now()
            val startOfDay = today.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val now = Instant.now()

            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = TotalCaloriesBurnedRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, now)
                )
            )

            response.records.sumOf { it.energy.inKilocalories }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading calories", e)
            0.0
        }
    }

    /**
     * Save step data to local database.
     */
    suspend fun saveStepData(stepData: StepData) {
        stepDataDao.insertStepData(stepData.generateId())
    }

    /**
     * Get step data for a user and date from local database.
     */
    suspend fun getStepData(userId: String, date: LocalDate): StepData? {
        return stepDataDao.getStepDataForDate(
            userId,
            date.format(DateTimeFormatter.ISO_DATE)
        )
    }

    /**
     * Get step data flow for today.
     */
    fun getTodayStepDataFlow(userId: String): Flow<StepData?> {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
        return stepDataDao.getStepDataForDateFlow(userId, today)
    }

    /**
     * Get step data for a date range.
     */
    fun getStepDataForRange(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Flow<List<StepData>> {
        return stepDataDao.getStepDataForRange(
            userId,
            startDate.format(DateTimeFormatter.ISO_DATE),
            endDate.format(DateTimeFormatter.ISO_DATE)
        )
    }

    /**
     * Get daily step summaries for chart display.
     */
    fun getDailyStepSummaries(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate,
        dailyGoal: Int
    ): Flow<List<DailyStepSummary>> {
        return getStepDataForRange(userId, startDate, endDate).map { stepDataList ->
            stepDataList.map { stepData ->
                DailyStepSummary(
                    date = LocalDate.parse(stepData.date),
                    steps = stepData.steps,
                    goal = dailyGoal
                )
            }
        }
    }

    /**
     * Get total steps for a date range.
     */
    suspend fun getTotalStepsForRange(
        userId: String,
        startDate: LocalDate,
        endDate: LocalDate
    ): Long {
        return stepDataDao.getTotalStepsForRange(
            userId,
            startDate.format(DateTimeFormatter.ISO_DATE),
            endDate.format(DateTimeFormatter.ISO_DATE)
        ) ?: 0L
    }

    /**
     * Sync today's data from Health Connect to local database.
     */
    suspend fun syncTodayData(userId: String, dailyGoal: Int): StepData {
        val steps = getTodayStepsFromHealthConnect()
        val distance = getTodayDistanceFromHealthConnect()
        val calories = getTodayCaloriesFromHealthConnect()
        val today = LocalDate.now()

        val stepData = StepData(
            userId = userId,
            date = today.format(DateTimeFormatter.ISO_DATE),
            steps = steps,
            distanceMeters = distance,
            caloriesBurned = calories,
            goalReached = steps >= dailyGoal,
            syncedToCloud = false
        ).generateId()

        saveStepData(stepData)
        return stepData
    }

    /**
     * Get unsynced data for cloud sync.
     */
    suspend fun getUnsyncedData(): List<StepData> {
        return stepDataDao.getUnsyncedData()
    }

    /**
     * Mark data as synced to cloud.
     */
    suspend fun markAsSynced(id: String) {
        stepDataDao.markAsSynced(id)
    }
}