package com.babytracker.data.local.entity

import com.babytracker.domain.model.VisitQuestion
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class VisitQuestionEntityMappingTest {
    @Test
    fun `inbox question round trips`() {
        val q = VisitQuestion(id = 1, text = "Is the rash normal?", answered = false, visitId = null, createdAt = Instant.ofEpochMilli(100))
        assertEquals(q, q.toEntity().toDomain())
    }

    @Test
    fun `attached answered question round trips`() {
        val q = VisitQuestion(id = 2, text = "Sleep regression?", answered = true, visitId = 9, createdAt = Instant.ofEpochMilli(200))
        assertEquals(q, q.toEntity().toDomain())
    }

    @Test
    fun `question with free-text answer round trips`() {
        val q = VisitQuestion(
            id = 3, text = "Rash?", answered = true, answer = "Eczema", visitId = 9, createdAt = Instant.ofEpochMilli(300),
        )
        assertEquals(q, q.toEntity().toDomain())
    }
}
