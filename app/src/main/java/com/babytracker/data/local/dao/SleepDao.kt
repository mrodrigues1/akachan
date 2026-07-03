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

    @Query("SELECT * FROM sleep_records WHERE start_time >= :sinceMillis ORDER BY start_time DESC")
    abstract fun getRecordsSinceFlow(sinceMillis: Long): Flow<List<SleepEntity>>

    @Query("SELECT * FROM sleep_records ORDER BY start_time DESC, id DESC LIMIT 1")
    abstract fun observeLatestRecord(): Flow<SleepEntity?>

    @Query("SELECT * FROM sleep_records ORDER BY start_time DESC, id DESC LIMIT 1")
    abstract suspend fun getLatestRecord(): SleepEntity?

    @Query("SELECT * FROM sleep_records WHERE id = :id LIMIT 1")
    abstract suspend fun getById(id: Long): SleepEntity?

    @Query("SELECT * FROM sleep_records WHERE client_id = :clientId LIMIT 1")
    abstract suspend fun getByClientId(clientId: String): SleepEntity?

    @Query("SELECT * FROM sleep_records WHERE end_time IS NULL LIMIT 1")
    abstract suspend fun getActiveRecord(): SleepEntity?

    @Query("SELECT * FROM sleep_records WHERE end_time IS NULL ORDER BY start_time DESC, id DESC LIMIT 1")
    abstract suspend fun getActiveRecordOnce(): SleepEntity?

    @Query("SELECT * FROM sleep_records ORDER BY start_time ASC")
    abstract suspend fun getAllRecordsOnce(): List<SleepEntity>

    // The extra start_time >= :startMillis - MAX_COMPLETED_SPAN_MS bound lets the start_time
    // index prune the scan (end_time is unindexed and callers pass end = now, so without it
    // every range query rescans the full history). ponytail: records longer than 48h fall out
    // of range queries; index end_time instead if that ceiling ever matters.
    @Query(
        "SELECT * FROM sleep_records " +
            "WHERE end_time IS NOT NULL AND start_time <= :endMillis AND end_time >= :startMillis " +
            "AND start_time >= :startMillis - $MAX_COMPLETED_SPAN_MS " +
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

    // Identity (clientId/startedBy) is set once at insert and preserved across every edit:
    // re-deriving it on update would corrupt the unique client_id index and lose attribution.
    // Local edits and partner-sync application reach this concurrently, so the identity read
    // and the write must share one transaction or the slower writer clobbers the faster one.
    @Transaction
    open suspend fun updateRecordPreservingIdentity(entity: SleepEntity) {
        val existing = getById(entity.id)
        val toSave = if (existing != null) {
            entity.copy(clientId = existing.clientId, startedBy = existing.startedBy)
        } else {
            entity
        }
        updateRecord(toSave)
    }

    @Transaction
    open suspend fun stopActiveRecord(endTime: Long): Boolean {
        val active = getActiveRecordOnce() ?: return false
        markRecordEnded(active.id, endTime)
        return true
    }

    companion object {
        /** Longest completed record a range query still finds when it crosses the window start. */
        const val MAX_COMPLETED_SPAN_MS = 48 * 60 * 60 * 1000L
    }
}
