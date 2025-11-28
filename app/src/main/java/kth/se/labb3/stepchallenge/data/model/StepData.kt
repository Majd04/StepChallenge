package kth.se.labb3.stepchallenge.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Step data record for a specific date.
 * Stored locally in Room database and synced to Firebase.
 */
@Entity(tableName = "step_data")
data class StepData(
    @PrimaryKey
    val id: String = "",
    val userId: String = "",
    val date: String = LocalDate.now().format(DateTimeFormatter.ISO_DATE),
    val steps: Long = 0,
    val caloriesBurned: Double = 0.0,
    val distanceMeters: Double = 0.0,
    val goalReached: Boolean = false,
    val syncedToCloud: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    /**
     * Generate ID from userId and date.
     */
    fun generateId(): StepData = copy(id = "${userId}_${date}")

    /**
     * Convert to Firebase map.
     */
    fun toFirebaseMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "userId" to userId,
        "date" to date,
        "steps" to steps,
        "caloriesBurned" to caloriesBurned,
        "distanceMeters" to distanceMeters,
        "goalReached" to goalReached,
        "lastUpdated" to lastUpdated
    )

    companion object {
        fun fromFirebaseMap(map: Map<String, Any?>): StepData = StepData(
            id = map["id"] as? String ?: "",
            userId = map["userId"] as? String ?: "",
            date = map["date"] as? String ?: LocalDate.now().format(DateTimeFormatter.ISO_DATE),
            steps = (map["steps"] as? Number)?.toLong() ?: 0,
            caloriesBurned = (map["caloriesBurned"] as? Number)?.toDouble() ?: 0.0,
            distanceMeters = (map["distanceMeters"] as? Number)?.toDouble() ?: 0.0,
            goalReached = map["goalReached"] as? Boolean ?: false,
            syncedToCloud = true,
            lastUpdated = (map["lastUpdated"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )

        fun createId(userId: String, date: LocalDate): String =
            "${userId}_${date.format(DateTimeFormatter.ISO_DATE)}"
    }
}

/**
 * Leaderboard entry for displaying in the UI.
 */
data class LeaderboardEntry(
    val rank: Int,
    val userId: String,
    val displayName: String,
    val photoUrl: String? = null,
    val steps: Long,
    val isCurrentUser: Boolean = false
)

/**
 * Period for leaderboard filtering.
 */
enum class LeaderboardPeriod {
    DAILY,
    WEEKLY,
    MONTHLY
}

/**
 * Daily step summary for charts.
 */
data class DailyStepSummary(
    val date: LocalDate,
    val steps: Long,
    val goal: Int,
    val percentOfGoal: Float = if (goal > 0) (steps.toFloat() / goal) * 100 else 0f
)