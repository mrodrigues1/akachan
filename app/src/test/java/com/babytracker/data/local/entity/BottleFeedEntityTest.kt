package com.babytracker.data.local.entity

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.FeedAuthor
import com.babytracker.domain.model.FeedType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class BottleFeedEntityTest {

    @Test
    fun `toEntity then toDomain round-trips all fields`() {
        val domain = BottleFeed(
            id = 7,
            clientId = "client-7",
            timestamp = Instant.ofEpochMilli(1_000),
            volumeMl = 120,
            type = FeedType.FORMULA,
            linkedMilkBagId = 42,
            notes = "half feed",
            createdAt = Instant.ofEpochMilli(2_000),
        )

        val restored = domain.toEntity().toDomain()

        assertEquals(domain, restored)
    }

    @Test
    fun `toDomain falls back on unknown enum values instead of crashing`() {
        val entity = BottleFeedEntity(
            id = 1,
            clientId = "client-1",
            timestamp = 1_000,
            volumeMl = 90,
            type = "GARBAGE",
            createdAt = 2_000,
            author = "garbage",
        )

        val domain = entity.toDomain()

        assertEquals(FeedType.FORMULA, domain.type)
        assertEquals(FeedAuthor.OWNER, domain.author)
    }

    @Test
    fun `toDomain maps null linkedMilkBagId and notes`() {
        val entity = BottleFeedEntity(
            id = 1,
            clientId = "client-1",
            timestamp = 1_000,
            volumeMl = 90,
            type = "BREAST_MILK",
            linkedMilkBagId = null,
            notes = null,
            createdAt = 2_000,
        )

        val domain = entity.toDomain()

        assertEquals(FeedType.BREAST_MILK, domain.type)
        assertEquals(null, domain.linkedMilkBagId)
        assertEquals(null, domain.notes)
    }
}
