package com.babytracker.sharing.domain.model

import com.babytracker.domain.model.InventorySummary
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class DomainToSnapshotInventoryTest {

    @Test
    fun toSnapshotFieldsPopulatesTotalMlBagCountAndUpdatedAt() {
        val summary = InventorySummary(
            totalMl = 320,
            bagCount = 3,
            oldestBagDate = Instant.parse("2026-05-15T10:00:00Z"),
        )
        val updatedAtMs = 1_716_000_000_000L

        val fields = summary.toSnapshotFields(updatedAtMs)

        assertEquals(320, fields.totalMl)
        assertEquals(3, fields.bagCount)
        assertEquals(updatedAtMs, fields.updatedAtMs)
    }

    @Test
    fun toSnapshotFieldsWithEmptySummary() {
        val fields = InventorySummary.Empty.toSnapshotFields(0L)

        assertEquals(0, fields.totalMl)
        assertEquals(0, fields.bagCount)
        assertEquals(0L, fields.updatedAtMs)
    }
}
