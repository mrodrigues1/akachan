package com.babytracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.toSleepTypeSafe
import java.time.Instant

@Entity(
    tableName = "sleep_records",
    // Every query filters/sorts on start_time (history, latest/active LIMIT 1, range scans). Without
    // an index these are full table scans + in-memory sorts that grow with a year of sleep records.
    indices = [Index(value = ["start_time"], orders = [Index.Order.DESC])],
)
data class SleepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long? = null,
    @ColumnInfo(name = "sleep_type") val sleepType: String,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "timezone_id") val timezoneId: String? = null
)

fun SleepEntity.toDomain(): SleepRecord = SleepRecord(
    id = id,
    startTime = Instant.ofEpochMilli(startTime),
    endTime = endTime?.let { Instant.ofEpochMilli(it) },
    sleepType = sleepType.toSleepTypeSafe(),
    notes = notes,
    timezoneId = timezoneId
)

fun SleepRecord.toEntity(): SleepEntity = SleepEntity(
    id = id,
    startTime = startTime.toEpochMilli(),
    endTime = endTime?.toEpochMilli(),
    sleepType = sleepType.name,
    notes = notes,
    timezoneId = timezoneId
)
