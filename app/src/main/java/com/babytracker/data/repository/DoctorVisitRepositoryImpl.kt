package com.babytracker.data.repository

import com.babytracker.data.local.dao.DoctorVisitDao
import com.babytracker.data.local.entity.toDomain
import com.babytracker.data.local.entity.toEntity
import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.model.VisitQuestion
import com.babytracker.domain.repository.DoctorVisitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DoctorVisitRepositoryImpl @Inject constructor(
    private val dao: DoctorVisitDao,
) : DoctorVisitRepository {
    override fun observeAllVisits(): Flow<List<DoctorVisit>> =
        dao.observeAllVisits().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getVisitById(id: Long): DoctorVisit? = dao.getVisitById(id)?.toDomain()

    override suspend fun getAllVisitsOnce(): List<DoctorVisit> = dao.getAllVisitsOnce().map { it.toDomain() }

    override suspend fun getUpcomingVisitsAfter(nowMs: Long): List<DoctorVisit> =
        dao.getUpcomingVisitsAfter(nowMs).map { it.toDomain() }

    override suspend fun insertVisit(visit: DoctorVisit): Long = dao.insertVisit(visit.toEntity())

    override suspend fun updateVisit(visit: DoctorVisit) = dao.updateVisit(visit.toEntity())

    override suspend fun deleteVisitDetachingQuestions(id: Long) {
        dao.deleteVisitDetachingQuestions(id)
    }

    override suspend fun insertVisitWithAttachments(visit: DoctorVisit, questionIds: List<Long>): Long =
        dao.insertVisitWithAttachments(visit.toEntity(), questionIds)

    override suspend fun updateVisitReconcilingAttachments(visit: DoctorVisit, questionIds: List<Long>) =
        dao.updateVisitReconcilingAttachments(visit.toEntity(), questionIds)

    override fun observeInboxQuestions(): Flow<List<VisitQuestion>> =
        dao.observeInboxQuestions().map { rows -> rows.map { it.toDomain() } }

    override fun observeQuestionsForVisit(visitId: Long): Flow<List<VisitQuestion>> =
        dao.observeQuestionsForVisit(visitId).map { rows -> rows.map { it.toDomain() } }

    override fun observeAttachedQuestionCounts(): Flow<Map<Long, Int>> =
        dao.observeAttachedQuestionCounts().map { rows -> rows.associate { it.visitId to it.count } }

    override suspend fun getAllQuestionsOnce(): List<VisitQuestion> = dao.getAllQuestionsOnce().map { it.toDomain() }

    override suspend fun getQuestionById(id: Long): VisitQuestion? = dao.getQuestionById(id)?.toDomain()

    override suspend fun insertQuestion(question: VisitQuestion): Long = dao.insertQuestion(question.toEntity())

    override suspend fun updateQuestion(question: VisitQuestion) = dao.updateQuestion(question.toEntity())

    override suspend fun deleteQuestionById(id: Long) {
        dao.deleteQuestionById(id)
    }
}
