package com.babytracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.babytracker.domain.model.Milestone
import java.time.LocalDate
import java.time.LocalTime

@Entity(
    tableName = "milestones",
    indices = [Index(value = ["date_epoch_day"])],
)
data class MilestoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "date_epoch_day") val dateEpochDay: Long,
    @ColumnInfo(name = "time_minute_of_day") val timeMinuteOfDay: Int? = null,
    @ColumnInfo(name = "photo_uri") val photoUri: String? = null,
    @ColumnInfo(name = "note") val note: String? = null,
)

fun MilestoneEntity.toDomain(): Milestone = Milestone(
    id = id,
    title = title,
    date = LocalDate.ofEpochDay(dateEpochDay),
    time = timeMinuteOfDay?.let { LocalTime.ofSecondOfDay(it.toLong() * SECONDS_PER_MINUTE) },
    photoUri = photoUri,
    note = note,
)

fun Milestone.toEntity(): MilestoneEntity = MilestoneEntity(
    id = id,
    title = title,
    dateEpochDay = date.toEpochDay(),
    timeMinuteOfDay = time?.let { it.hour * MINUTES_PER_HOUR + it.minute },
    photoUri = photoUri,
    note = note,
)

private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60
