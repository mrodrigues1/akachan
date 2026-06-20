package com.babytracker.domain.repository

import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.model.VisitQuestion
import kotlinx.coroutines.flow.Flow

interface DoctorVisitRepository {
    // Visits
    fun observeAllVisits(): Flow<List<DoctorVisit>>
    suspend fun getVisitById(id: Long): DoctorVisit?
    suspend fun getAllVisitsOnce(): List<DoctorVisit>
    suspend fun getUpcomingVisitsAfter(nowMs: Long): List<DoctorVisit>
    suspend fun insertVisit(visit: DoctorVisit): Long
    suspend fun updateVisit(visit: DoctorVisit)
    suspend fun deleteVisitDetachingQuestions(id: Long)
    suspend fun insertVisitWithAttachments(visit: DoctorVisit, questionIds: List<Long>): Long
    suspend fun updateVisitReconcilingAttachments(visit: DoctorVisit, questionIds: List<Long>)

    // Questions
    fun observeInboxQuestions(): Flow<List<VisitQuestion>>
    fun observeQuestionsForVisit(visitId: Long): Flow<List<VisitQuestion>>
    fun observeAttachedQuestionCounts(): Flow<Map<Long, Int>>
    suspend fun getAllQuestionsOnce(): List<VisitQuestion>
    suspend fun getQuestionById(id: Long): VisitQuestion?
    suspend fun insertQuestion(question: VisitQuestion): Long
    suspend fun updateQuestion(question: VisitQuestion)
    suspend fun deleteQuestionById(id: Long)
}
