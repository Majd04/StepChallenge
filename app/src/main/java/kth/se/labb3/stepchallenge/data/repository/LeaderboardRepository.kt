package kth.se.labb3.stepchallenge.data.repository

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kth.se.labb3.stepchallenge.data.model.LeaderboardEntry
import kth.se.labb3.stepchallenge.data.model.LeaderboardPeriod
import kth.se.labb3.stepchallenge.data.model.StepData
import kth.se.labb3.stepchallenge.data.model.User
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Repository for managing leaderboard data in Firebase Firestore.
 */
class LeaderboardRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        private const val TAG = "LeaderboardRepository"
        private const val USERS_COLLECTION = "users"
        private const val STEP_DATA_COLLECTION = "step_data"
    }

    /**
     * Create or update user in Firestore.
     */
    suspend fun createOrUpdateUser(user: User): Result<Unit> {
        return try {
            firestore.collection(USERS_COLLECTION)
                .document(user.id)
                .set(user.toFirebaseMap())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating/updating user", e)
            Result.failure(e)
        }
    }

    /**
     * Get user from Firestore.
     */
    suspend fun getUser(userId: String): User? {
        return try {
            val doc = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .get()
                .await()

            doc.data?.let { User.fromFirebaseMap(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user", e)
            null
        }
    }

    /**
     * Sync step data to Firestore.
     */
    suspend fun syncStepData(stepData: StepData): Result<Unit> {
        return try {
            firestore.collection(STEP_DATA_COLLECTION)
                .document(stepData.id)
                .set(stepData.toFirebaseMap())
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing step data", e)
            Result.failure(e)
        }
    }

    /**
     * Update user's weekly and monthly step counts.
     */
    suspend fun updateUserStepCounts(
        userId: String,
        weeklySteps: Long,
        monthlySteps: Long,
        totalSteps: Long
    ): Result<Unit> {
        return try {
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .update(
                    mapOf(
                        "weeklySteps" to weeklySteps,
                        "monthlySteps" to monthlySteps,
                        "totalSteps" to totalSteps,
                        "lastUpdated" to System.currentTimeMillis()
                    )
                )
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating step counts", e)
            Result.failure(e)
        }
    }

    /**
     * Get weekly leaderboard as Flow.
     */
    fun getWeeklyLeaderboardFlow(currentUserId: String, limit: Int = 50): Flow<List<LeaderboardEntry>> {
        return callbackFlow {
            val listener = firestore.collection(USERS_COLLECTION)
                .orderBy("weeklySteps", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error listening to weekly leaderboard", error)
                        return@addSnapshotListener
                    }

                    val entries = snapshot?.documents?.mapIndexed { index, doc ->
                        val user = doc.data?.let { User.fromFirebaseMap(it) }
                        LeaderboardEntry(
                            rank = index + 1,
                            userId = user?.id ?: "",
                            displayName = user?.displayName ?: "Unknown",
                            photoUrl = user?.photoUrl,
                            steps = user?.weeklySteps ?: 0,
                            isCurrentUser = user?.id == currentUserId
                        )
                    } ?: emptyList()

                    trySend(entries)
                }

            awaitClose { listener.remove() }
        }
    }

    /**
     * Get monthly leaderboard as Flow.
     */
    fun getMonthlyLeaderboardFlow(currentUserId: String, limit: Int = 50): Flow<List<LeaderboardEntry>> {
        return callbackFlow {
            val listener = firestore.collection(USERS_COLLECTION)
                .orderBy("monthlySteps", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error listening to monthly leaderboard", error)
                        return@addSnapshotListener
                    }

                    val entries = snapshot?.documents?.mapIndexed { index, doc ->
                        val user = doc.data?.let { User.fromFirebaseMap(it) }
                        LeaderboardEntry(
                            rank = index + 1,
                            userId = user?.id ?: "",
                            displayName = user?.displayName ?: "Unknown",
                            photoUrl = user?.photoUrl,
                            steps = user?.monthlySteps ?: 0,
                            isCurrentUser = user?.id == currentUserId
                        )
                    } ?: emptyList()

                    trySend(entries)
                }

            awaitClose { listener.remove() }
        }
    }

    /**
     * Get leaderboard based on period.
     */
    fun getLeaderboardFlow(
        period: LeaderboardPeriod,
        currentUserId: String,
        limit: Int = 50
    ): Flow<List<LeaderboardEntry>> {
        return when (period) {
            LeaderboardPeriod.WEEKLY -> getWeeklyLeaderboardFlow(currentUserId, limit)
            LeaderboardPeriod.MONTHLY -> getMonthlyLeaderboardFlow(currentUserId, limit)
            LeaderboardPeriod.DAILY -> getDailyLeaderboardFlow(currentUserId, limit)
        }
    }

    /**
     * Get daily leaderboard (today's steps).
     */
    private fun getDailyLeaderboardFlow(currentUserId: String, limit: Int): Flow<List<LeaderboardEntry>> {
        val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)

        return callbackFlow {
            val listener = firestore.collection(STEP_DATA_COLLECTION)
                .whereEqualTo("date", today)
                .orderBy("steps", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error listening to daily leaderboard", error)
                        return@addSnapshotListener
                    }

                    val entries = snapshot?.documents?.mapIndexed { index, doc ->
                        val stepData = doc.data?.let { StepData.fromFirebaseMap(it) }
                        LeaderboardEntry(
                            rank = index + 1,
                            userId = stepData?.userId ?: "",
                            displayName = "User",
                            steps = stepData?.steps ?: 0,
                            isCurrentUser = stepData?.userId == currentUserId
                        )
                    } ?: emptyList()

                    trySend(entries)
                }

            awaitClose { listener.remove() }
        }
    }

    /**
     * Get current user's rank for a period.
     */
    suspend fun getUserRank(userId: String, period: LeaderboardPeriod): Int {
        return try {
            val field = when (period) {
                LeaderboardPeriod.DAILY -> return getUserDailyRank(userId)
                LeaderboardPeriod.WEEKLY -> "weeklySteps"
                LeaderboardPeriod.MONTHLY -> "monthlySteps"
            }

            val user = getUser(userId) ?: return 0
            val userSteps = when (period) {
                LeaderboardPeriod.WEEKLY -> user.weeklySteps
                LeaderboardPeriod.MONTHLY -> user.monthlySteps
                else -> 0L
            }

            val higherCount = firestore.collection(USERS_COLLECTION)
                .whereGreaterThan(field, userSteps)
                .get()
                .await()
                .size()

            higherCount + 1
        } catch (e: Exception) {
            Log.e(TAG, "Error getting user rank", e)
            0
        }
    }

    private suspend fun getUserDailyRank(userId: String): Int {
        return try {
            val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)

            val userDoc = firestore.collection(STEP_DATA_COLLECTION)
                .document("${userId}_${today}")
                .get()
                .await()

            val userSteps = userDoc.getLong("steps") ?: 0L

            val higherCount = firestore.collection(STEP_DATA_COLLECTION)
                .whereEqualTo("date", today)
                .whereGreaterThan("steps", userSteps)
                .get()
                .await()
                .size()

            higherCount + 1
        } catch (e: Exception) {
            Log.e(TAG, "Error getting daily rank", e)
            0
        }
    }

    /**
     * Calculate steps needed to reach a specific rank.
     */
    suspend fun getStepsNeededForRank(
        targetRank: Int,
        period: LeaderboardPeriod,
        currentUserId: String
    ): Long? {
        return try {
            val field = when (period) {
                LeaderboardPeriod.WEEKLY -> "weeklySteps"
                LeaderboardPeriod.MONTHLY -> "monthlySteps"
                LeaderboardPeriod.DAILY -> return getStepsNeededForDailyRank(targetRank, currentUserId)
            }

            val leaders = firestore.collection(USERS_COLLECTION)
                .orderBy(field, Query.Direction.DESCENDING)
                .limit(targetRank.toLong())
                .get()
                .await()

            if (leaders.size() >= targetRank) {
                val targetUser = leaders.documents[targetRank - 1]
                val targetSteps = targetUser.getLong(field) ?: 0L

                val currentUser = getUser(currentUserId)
                val currentSteps = when (period) {
                    LeaderboardPeriod.WEEKLY -> currentUser?.weeklySteps ?: 0L
                    LeaderboardPeriod.MONTHLY -> currentUser?.monthlySteps ?: 0L
                    else -> 0L
                }

                if (targetSteps > currentSteps) {
                    targetSteps - currentSteps + 1
                } else {
                    0L
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating steps needed", e)
            null
        }
    }

    private suspend fun getStepsNeededForDailyRank(targetRank: Int, currentUserId: String): Long? {
        return try {
            val today = LocalDate.now().format(DateTimeFormatter.ISO_DATE)

            val leaders = firestore.collection(STEP_DATA_COLLECTION)
                .whereEqualTo("date", today)
                .orderBy("steps", Query.Direction.DESCENDING)
                .limit(targetRank.toLong())
                .get()
                .await()

            if (leaders.size() >= targetRank) {
                val targetDoc = leaders.documents[targetRank - 1]
                val targetSteps = targetDoc.getLong("steps") ?: 0L

                val currentDoc = firestore.collection(STEP_DATA_COLLECTION)
                    .document("${currentUserId}_${today}")
                    .get()
                    .await()
                val currentSteps = currentDoc.getLong("steps") ?: 0L

                if (targetSteps > currentSteps) {
                    targetSteps - currentSteps + 1
                } else {
                    0L
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating daily steps needed", e)
            null
        }
    }
}