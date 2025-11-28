package kth.se.labb3.stepchallenge.data.DAO.

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import se.kth.stepchallenge.data.model.StepData
import se.kth.stepchallenge.data.model.User

/**
 * DAO for User operations.
 */
@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUser(userId: String): User?

    @Query("SELECT * FROM users WHERE id = :userId")
    fun getUserFlow(userId: String): Flow<User?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Update
    suspend fun updateUser(user: User)

    @Delete
    suspend fun deleteUser(user: User)

    @Query("DELETE FROM users")
    suspend fun deleteAllUsers()
}

/**
 * DAO for StepData operations.
 */
@Dao
interface StepDataDao {
    @Query("SELECT * FROM step_data WHERE userId = :userId ORDER BY date DESC")
    fun getStepDataForUser(userId: String): Flow<List<StepData>>

    @Query("SELECT * FROM step_data WHERE userId = :userId AND date = :date")
    suspend fun getStepDataForDate(userId: String, date: String): StepData?

    @Query("SELECT * FROM step_data WHERE userId = :userId AND date = :date")
    fun getStepDataForDateFlow(userId: String, date: String): Flow<StepData?>

    @Query("SELECT * FROM step_data WHERE userId = :userId AND date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun getStepDataForRange(userId: String, startDate: String, endDate: String): Flow<List<StepData>>

    @Query("SELECT SUM(steps) FROM step_data WHERE userId = :userId AND date BETWEEN :startDate AND :endDate")
    suspend fun getTotalStepsForRange(userId: String, startDate: String, endDate: String): Long?

    @Query("SELECT * FROM step_data WHERE syncedToCloud = 0")
    suspend fun getUnsyncedData(): List<StepData>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStepData(stepData: StepData)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllStepData(stepDataList: List<StepData>)

    @Update
    suspend fun updateStepData(stepData: StepData)

    @Query("UPDATE step_data SET syncedToCloud = 1 WHERE id = :id")
    suspend fun markAsSynced(id: String)

    @Query("DELETE FROM step_data WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}

/**
 * Room Database for Step Challenge app.
 */
@Database(
    entities = [User::class, StepData::class],
    version = 1,
    exportSchema = false
)
abstract class StepChallengeDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun stepDataDao(): StepDataDao

    companion object {
        @Volatile
        private var INSTANCE: StepChallengeDatabase? = null

        fun getDatabase(context: Context): StepChallengeDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StepChallengeDatabase::class.java,
                    "step_challenge_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}