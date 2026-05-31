package com.babytracker.data.local.dao

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.babytracker.data.local.entity.SleepEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SleepDao {
    @Query("SELECT * FROM sleep_records ORDER BY start_time DESC")
    abstract fun getAllRecords(): Flow<List<SleepEntity>>

    @Query("SELECT * FROM sleep_records WHERE start_time >= :sinceMillis AND end_time IS NOT NULL ORDER BY start_time ASC")
    abstract suspend fun getCompletedRecordsSince(sinceMillis: Long): List<SleepEntity>

    @Query("SELECT * FROM sleep_records ORDER BY start_time DESC LIMIT :limit")
    abstract suspend fun getRecentRecords(limit: Int): List<SleepEntity>

    @Query("SELECT * FROM sleep_records ORDER BY start_time DESC, id DESC LIMIT 1")
    abstract fun observeLatestRecord(): Flow<SleepEntity?>

    @Query("SELECT * FROM sleep_records ORDER BY start_time DESC, id DESC LIMIT 1")
    abstract suspend fun getLatestRecord(): SleepEntity?

    @Query("SELECT * FROM sleep_records WHERE end_time IS NULL LIMIT 1")
    abstract suspend fun getActiveRecord(): SleepEntity?

    @Query("SELECT * FROM sleep_records WHERE end_time IS NULL ORDER BY start_time DESC, id DESC LIMIT 1")
    abstract suspend fun getActiveRecordOnce(): SleepEntity?

    @Query("SELECT * FROM sleep_records ORDER BY start_time ASC")
    abstract suspend fun getAllRecordsOnce(): List<SleepEntity>

    @Query(
        "SELECT * FROM sleep_records " +
            "WHERE end_time IS NOT NULL AND start_time <= :endMillis AND end_time >= :startMillis " +
            "ORDER BY start_time ASC",
    )
    abstract suspend fun getCompletedRecordsBetween(startMillis: Long, endMillis: Long): List<SleepEntity>

    @Insert
    abstract suspend fun insertRecord(entity: SleepEntity): Long

    @Update
    abstract suspend fun updateRecord(entity: SleepEntity)

    @Query("DELETE FROM sleep_records WHERE id = :id")
    abstract suspend fun deleteRecord(id: Long)

    @Query("UPDATE sleep_records SET end_time = :endTime WHERE id = :id")
    abstract suspend fun markRecordEnded(id: Long, endTime: Long)

    @Transaction
    open suspend fun startRecordIfNone(entity: SleepEntity): Long? {
        if (getActiveRecordOnce() != null) return null
        return try {
            insertRecord(entity)
        } catch (e: SQLiteConstraintException) {
            if (getActiveRecordOnce() != null) null else throw e
        }
    }

    @Transaction
    open suspend fun stopActiveRecord(endTime: Long): Boolean {
        val active = getActiveRecordOnce() ?: return false
        markRecordEnded(active.id, endTime)
        return true
    }
}
