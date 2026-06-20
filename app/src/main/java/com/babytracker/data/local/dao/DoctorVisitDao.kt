package com.babytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.babytracker.data.local.entity.DoctorVisitEntity
import com.babytracker.data.local.entity.VisitQuestionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DoctorVisitDao {
    // ── Visits ──
    @Query("SELECT * FROM doctor_visits ORDER BY date DESC")
    fun observeAllVisits(): Flow<List<DoctorVisitEntity>>

    @Query("SELECT * FROM doctor_visits WHERE id = :id LIMIT 1")
    suspend fun getVisitById(id: Long): DoctorVisitEntity?

    @Query("SELECT * FROM doctor_visits ORDER BY date ASC")
    suspend fun getAllVisitsOnce(): List<DoctorVisitEntity>

    @Query("SELECT * FROM doctor_visits WHERE date > :nowMs ORDER BY date ASC")
    suspend fun getUpcomingVisitsAfter(nowMs: Long): List<DoctorVisitEntity>

    @Insert
    suspend fun insertVisit(entity: DoctorVisitEntity): Long

    @Update
    suspend fun updateVisit(entity: DoctorVisitEntity)

    @Query("DELETE FROM doctor_visits WHERE id = :id")
    suspend fun deleteVisitById(id: Long): Int

    // ── Questions ──
    @Query("SELECT * FROM visit_questions WHERE visit_id IS NULL ORDER BY created_at DESC")
    fun observeInboxQuestions(): Flow<List<VisitQuestionEntity>>

    @Query("SELECT * FROM visit_questions WHERE visit_id = :visitId ORDER BY created_at ASC")
    fun observeQuestionsForVisit(visitId: Long): Flow<List<VisitQuestionEntity>>

    @Query("SELECT * FROM visit_questions ORDER BY created_at ASC")
    suspend fun getAllQuestionsOnce(): List<VisitQuestionEntity>

    @Query("SELECT * FROM visit_questions WHERE id = :id LIMIT 1")
    suspend fun getQuestionById(id: Long): VisitQuestionEntity?

    @Insert
    suspend fun insertQuestion(entity: VisitQuestionEntity): Long

    @Update
    suspend fun updateQuestion(entity: VisitQuestionEntity)

    @Query("DELETE FROM visit_questions WHERE id = :id")
    suspend fun deleteQuestionById(id: Long): Int

    @Query("UPDATE visit_questions SET visit_id = :visitId WHERE id IN (:ids)")
    suspend fun attachQuestions(ids: List<Long>, visitId: Long)

    @Query("UPDATE visit_questions SET visit_id = NULL WHERE visit_id = :visitId")
    suspend fun detachQuestionsForVisit(visitId: Long)

    /**
     * Atomic delete: detach this visit's questions back to the inbox and delete the visit in
     * one DB transaction. The integrity boundary for the FK-less relationship — without this,
     * a crash between detach and delete could orphan the visit with its questions already moved.
     */
    @Transaction
    suspend fun deleteVisitDetachingQuestions(visitId: Long) {
        detachQuestionsForVisit(visitId)
        deleteVisitById(visitId)
    }

    /** Atomic create: insert the visit, then attach the selected questions in one transaction. */
    @Transaction
    suspend fun insertVisitWithAttachments(entity: DoctorVisitEntity, questionIds: List<Long>): Long {
        val id = insertVisit(entity)
        if (questionIds.isNotEmpty()) attachQuestions(questionIds, id)
        return id
    }

    /** Atomic edit: update the visit and reconcile attachments (detach all, re-attach selection). */
    @Transaction
    suspend fun updateVisitReconcilingAttachments(entity: DoctorVisitEntity, questionIds: List<Long>) {
        updateVisit(entity)
        detachQuestionsForVisit(entity.id)
        if (questionIds.isNotEmpty()) attachQuestions(questionIds, entity.id)
    }
}
