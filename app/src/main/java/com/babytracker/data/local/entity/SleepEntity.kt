package com.babytracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.babytracker.domain.model.SleepAuthor
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.toSleepTypeSafe
import java.time.Instant
import java.util.UUID

@Entity(
    tableName = "sleep_records",
    // Every query filters/sorts on start_time (history, latest/active LIMIT 1, range scans). Without
    // an index these are full table scans + in-memory sorts that grow with a year of sleep records.
    // client_id is unique so a partner-started session can be upserted idempotently across devices.
    indices = [
        Index(value = ["start_time"], orders = [Index.Order.DESC]),
        Index(value = ["client_id"], unique = true),
    ],
)
data class SleepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long? = null,
    @ColumnInfo(name = "sleep_type") val sleepType: String,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "timezone_id") val timezoneId: String? = null,
    // Kotlin default mints a unique id for any direct construction so the unique index can't be
    // tripped by a blank duplicate. Room ignores this default (it always supplies the column value);
    // the SQL-level default '' only governs the migration backfill.
    @ColumnInfo(name = "client_id", defaultValue = "''") val clientId: String = UUID.randomUUID().toString(),
    @ColumnInfo(name = "started_by", defaultValue = "'OWNER'") val startedBy: String = SleepAuthor.OWNER.name,
)

// Lenient enum parsing: one corrupt row must not crash every sleep list it appears in.
fun SleepEntity.toDomain(): SleepRecord = SleepRecord(
    id = id,
    startTime = Instant.ofEpochMilli(startTime),
    endTime = endTime?.let { Instant.ofEpochMilli(it) },
    sleepType = sleepType.toSleepTypeSafe(),
    notes = notes,
    timezoneId = timezoneId,
    clientId = clientId,
    startedBy = SleepAuthor.entries.find { it.name == startedBy } ?: SleepAuthor.OWNER,
)

fun SleepRecord.toEntity(): SleepEntity = SleepEntity(
    id = id,
    startTime = startTime.toEpochMilli(),
    endTime = endTime?.toEpochMilli(),
    sleepType = sleepType.name,
    notes = notes,
    timezoneId = timezoneId,
    clientId = clientId,
    startedBy = startedBy.name,
)
