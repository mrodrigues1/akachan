package com.babytracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.babytracker.domain.model.BabyProfile
import java.time.LocalDate

const val SINGLETON_ID = 1L

@Entity(tableName = "babies")
data class BabyProfileEntity(
    @PrimaryKey val id: Long = SINGLETON_ID,
    @ColumnInfo(name = "date_of_birth") val dateOfBirth: Long? = null,
    @ColumnInfo(name = "due_date") val dueDate: Long? = null,
    @ColumnInfo(name = "due_date_user_provided", defaultValue = "0") val isDueDateUserProvided: Boolean = false,
    @ColumnInfo(name = "home_timezone_id") val homeTimezoneId: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

fun BabyProfileEntity.toDomain(): BabyProfile = BabyProfile(
    dateOfBirth = dateOfBirth?.let { LocalDate.ofEpochDay(it / MILLIS_PER_DAY) },
    dueDate = dueDate?.let { LocalDate.ofEpochDay(it / MILLIS_PER_DAY) },
    isDueDateUserProvided = isDueDateUserProvided,
    homeTimezoneId = homeTimezoneId,
    createdAtEpochMs = createdAt,
    updatedAtEpochMs = updatedAt,
)

fun BabyProfile.toEntity(): BabyProfileEntity = BabyProfileEntity(
    id = SINGLETON_ID,
    dateOfBirth = dateOfBirth?.toEpochDay()?.times(MILLIS_PER_DAY),
    dueDate = dueDate?.toEpochDay()?.times(MILLIS_PER_DAY),
    isDueDateUserProvided = isDueDateUserProvided,
    homeTimezoneId = homeTimezoneId,
    createdAt = createdAtEpochMs,
    updatedAt = updatedAtEpochMs,
)

private const val MILLIS_PER_DAY = 86_400_000L
