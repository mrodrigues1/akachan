package com.babytracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.babytracker.domain.model.Milestone
import com.babytracker.domain.model.MilestoneAchievement
import java.time.LocalDate

@Entity(
    tableName = "milestone_achievements",
    indices = [Index(value = ["milestone"], unique = true)],
)
data class MilestoneAchievementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "milestone") val milestone: String,
    @ColumnInfo(name = "achieved_on_epoch_day") val achievedOnEpochDay: Long,
    @ColumnInfo(name = "photo_uri") val photoUri: String? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
)

/** Returns null when the stored milestone name no longer maps to an enum value. */
fun MilestoneAchievementEntity.toDomainOrNull(): MilestoneAchievement? {
    val resolved = Milestone.entries.firstOrNull { it.name == milestone } ?: return null
    return MilestoneAchievement(
        id = id,
        milestone = resolved,
        achievedOn = LocalDate.ofEpochDay(achievedOnEpochDay),
        photoUri = photoUri,
        notes = notes,
    )
}

fun MilestoneAchievement.toEntity(): MilestoneAchievementEntity = MilestoneAchievementEntity(
    id = id,
    milestone = milestone.name,
    achievedOnEpochDay = achievedOn.toEpochDay(),
    photoUri = photoUri,
    notes = notes,
)
