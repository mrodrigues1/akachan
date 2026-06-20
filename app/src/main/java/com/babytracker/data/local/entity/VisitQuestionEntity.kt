package com.babytracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.babytracker.domain.model.VisitQuestion
import java.time.Instant

@Entity(
    tableName = "visit_questions",
    indices = [Index(value = ["visit_id"])],
)
data class VisitQuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "text") val text: String,
    @ColumnInfo(name = "answered") val answered: Boolean = false,
    @ColumnInfo(name = "visit_id") val visitId: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

fun VisitQuestionEntity.toDomain(): VisitQuestion = VisitQuestion(
    id = id,
    text = text,
    answered = answered,
    visitId = visitId,
    createdAt = Instant.ofEpochMilli(createdAt),
)

fun VisitQuestion.toEntity(): VisitQuestionEntity = VisitQuestionEntity(
    id = id,
    text = text,
    answered = answered,
    visitId = visitId,
    createdAt = createdAt.toEpochMilli(),
)
