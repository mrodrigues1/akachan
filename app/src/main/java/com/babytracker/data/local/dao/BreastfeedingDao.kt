package com.babytracker.data.local.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.babytracker.data.local.entity.BreastfeedingEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class BreastfeedingDao {
    @Query("SELECT * FROM breastfeeding_sessions ORDER BY start_time DESC")
    abstract fun getAllSessions(): Flow<List<BreastfeedingEntity>>

    @Query("SELECT * FROM breastfeeding_sessions WHERE end_time IS NULL LIMIT 1")
    abstract fun getActiveSession(): Flow<BreastfeedingEntity?>

    @Query("SELECT * FROM breastfeeding_sessions WHERE end_time IS NULL LIMIT 1")
    abstract suspend fun getActiveSessionOnce(): BreastfeedingEntity?

    @Query("SELECT * FROM breastfeeding_sessions ORDER BY start_time DESC LIMIT 1")
    abstract fun observeLatestSession(): Flow<BreastfeedingEntity?>

    @Query("SELECT * FROM breastfeeding_sessions ORDER BY start_time DESC LIMIT 1")
    abstract suspend fun getLastSession(): BreastfeedingEntity?

    @Query("SELECT * FROM breastfeeding_sessions ORDER BY start_time DESC LIMIT :limit")
    abstract suspend fun getRecentSessions(limit: Int): List<BreastfeedingEntity>

    @Query("SELECT * FROM breastfeeding_sessions ORDER BY start_time ASC")
    abstract suspend fun getAllSessionsOnce(): List<BreastfeedingEntity>

    @Query(
        "SELECT * FROM breastfeeding_sessions " +
            "WHERE end_time IS NOT NULL AND start_time <= :endMillis AND end_time >= :startMillis " +
            "ORDER BY start_time ASC",
    )
    abstract suspend fun getCompletedSessionsBetween(startMillis: Long, endMillis: Long): List<BreastfeedingEntity>

    @Insert
    abstract suspend fun insertSession(entity: BreastfeedingEntity): Long

    @Update
    abstract suspend fun updateSession(entity: BreastfeedingEntity)

    @Delete
    abstract suspend fun deleteSession(entity: BreastfeedingEntity)

    @Query("UPDATE breastfeeding_sessions SET end_time = :endTime WHERE id = :id")
    abstract suspend fun markSessionEnded(id: Long, endTime: Long)

    @Transaction
    open suspend fun startSessionIfNone(entity: BreastfeedingEntity): Long? {
        if (getActiveSessionOnce() != null) return null
        return try {
            insertSession(entity)
        } catch (e: SQLiteConstraintException) {
            if (getActiveSessionOnce() != null) null else throw e
        }
    }

    @Transaction
    open suspend fun stopActiveSession(endTime: Long): Boolean {
        val active = getActiveSessionOnce() ?: return false
        markSessionEnded(active.id, endTime)
        return true
    }
}
