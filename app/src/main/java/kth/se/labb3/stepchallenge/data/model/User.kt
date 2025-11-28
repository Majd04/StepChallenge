package kth.se.labb3.stepchallenge.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * User model representing a user in the Step Challenge app.
 * Stored both locally (Room) and remotely (Firebase Firestore).
 */
@Entity(tableName = "users")
data class User(
    @PrimaryKey
    val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val totalSteps: Long = 0,
    val weeklySteps: Long = 0,
    val monthlySteps: Long = 0,
    val dailyGoal: Int = 10000,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUpdated: Long = System.currentTimeMillis()
) {
    /**
     * Convert to a map for Firebase Firestore.
     */
    fun toFirebaseMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "email" to email,
        "displayName" to displayName,
        "photoUrl" to photoUrl,
        "totalSteps" to totalSteps,
        "weeklySteps" to weeklySteps,
        "monthlySteps" to monthlySteps,
        "dailyGoal" to dailyGoal,
        "createdAt" to createdAt,
        "lastUpdated" to lastUpdated
    )

    companion object {
        /**
         * Create User from Firebase document.
         */
        fun fromFirebaseMap(map: Map<String, Any?>): User = User(
            id = map["id"] as? String ?: "",
            email = map["email"] as? String ?: "",
            displayName = map["displayName"] as? String ?: "",
            photoUrl = map["photoUrl"] as? String,
            totalSteps = (map["totalSteps"] as? Number)?.toLong() ?: 0,
            weeklySteps = (map["weeklySteps"] as? Number)?.toLong() ?: 0,
            monthlySteps = (map["monthlySteps"] as? Number)?.toLong() ?: 0,
            dailyGoal = (map["dailyGoal"] as? Number)?.toInt() ?: 10000,
            createdAt = (map["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            lastUpdated = (map["lastUpdated"] as? Number)?.toLong() ?: System.currentTimeMillis()
        )
    }
}