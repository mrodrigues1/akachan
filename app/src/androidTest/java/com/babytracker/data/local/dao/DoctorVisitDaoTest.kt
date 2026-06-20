package com.babytracker.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.data.local.BabyTrackerDatabase
import com.babytracker.data.local.entity.DoctorVisitEntity
import com.babytracker.data.local.entity.VisitQuestionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DoctorVisitDaoTest {
    private lateinit var db: BabyTrackerDatabase
    private lateinit var dao: DoctorVisitDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            BabyTrackerDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.doctorVisitDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun visitOrderingAndUpcomingFilter() = runTest {
        dao.insertVisit(DoctorVisitEntity(date = 100, createdAt = 1))
        dao.insertVisit(DoctorVisitEntity(date = 5_000, createdAt = 1))
        val all = dao.observeAllVisits().first()
        assertEquals(listOf(5_000L, 100L), all.map { it.date }) // DESC
        val upcoming = dao.getUpcomingVisitsAfter(1_000)
        assertEquals(listOf(5_000L), upcoming.map { it.date })
    }

    @Test
    fun questionInboxAttachDetach() = runTest {
        val visitId = dao.insertVisit(DoctorVisitEntity(date = 100, createdAt = 1))
        val q1 = dao.insertQuestion(VisitQuestionEntity(text = "A", visitId = null, createdAt = 1))
        dao.insertQuestion(VisitQuestionEntity(text = "B", visitId = null, createdAt = 2))
        assertEquals(2, dao.observeInboxQuestions().first().size)

        dao.attachQuestions(listOf(q1), visitId)
        assertEquals(1, dao.observeInboxQuestions().first().size)
        assertEquals(listOf("A"), dao.observeQuestionsForVisit(visitId).first().map { it.text })

        dao.detachQuestionsForVisit(visitId)
        assertEquals(2, dao.observeInboxQuestions().first().size)
    }

    @Test
    fun observeAttachedQuestionCountsGroupsByVisit() = runTest {
        val v1 = dao.insertVisit(DoctorVisitEntity(date = 100, createdAt = 1))
        val v2 = dao.insertVisit(DoctorVisitEntity(date = 200, createdAt = 1))
        dao.insertQuestion(VisitQuestionEntity(text = "a", visitId = v1, createdAt = 1))
        dao.insertQuestion(VisitQuestionEntity(text = "b", visitId = v1, createdAt = 2))
        dao.insertQuestion(VisitQuestionEntity(text = "c", visitId = v2, createdAt = 3))
        dao.insertQuestion(VisitQuestionEntity(text = "inbox", visitId = null, createdAt = 4))

        val counts = dao.observeAttachedQuestionCounts().first().associate { it.visitId to it.count }
        assertEquals(2, counts[v1])
        assertEquals(1, counts[v2])
        assertEquals(null, counts[-1L]) // inbox questions excluded
    }

    @Test
    fun deleteVisitDetachingQuestionsIsAtomic() = runTest {
        val visitId = dao.insertVisit(DoctorVisitEntity(date = 100, createdAt = 1))
        dao.insertQuestion(VisitQuestionEntity(text = "A", visitId = visitId, createdAt = 1))
        dao.deleteVisitDetachingQuestions(visitId)
        assertEquals(null, dao.getVisitById(visitId)) // visit gone
        assertEquals(1, dao.observeInboxQuestions().first().size) // question back in inbox, not deleted
    }
}
