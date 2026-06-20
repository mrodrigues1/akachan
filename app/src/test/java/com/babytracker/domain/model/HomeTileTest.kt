package com.babytracker.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HomeTileTest {

    @Test
    fun `deserialize null returns default order`() {
        assertEquals(HomeTile.DEFAULT_ORDER, HomeTile.deserialize(null))
    }

    @Test
    fun `deserialize blank returns default order`() {
        assertEquals(HomeTile.DEFAULT_ORDER, HomeTile.deserialize("   "))
    }

    @Test
    fun `serialize then deserialize round-trips a custom order`() {
        val custom = listOf(HomeTile.SLEEP, HomeTile.BREASTFEEDING) +
            HomeTile.DEFAULT_ORDER.filter { it != HomeTile.SLEEP && it != HomeTile.BREASTFEEDING }
        assertEquals(custom, HomeTile.deserialize(HomeTile.serialize(custom)))
    }

    @Test
    fun `reconcile drops unknown stored names`() {
        val raw = "SLEEP,GHOST_TILE,BREASTFEEDING"
        val result = HomeTile.deserialize(raw)
        assertEquals(HomeTile.SLEEP, result[0])
        assertEquals(HomeTile.BREASTFEEDING, result[1])
        assertEquals(HomeTile.DEFAULT_ORDER.size, result.size)
        assertEquals(HomeTile.entries.toSet(), result.toSet())
    }

    @Test
    fun `reconcile appends tiles missing from a stale stored order`() {
        val result = HomeTile.deserialize("PARTNER,SLEEP")
        assertEquals(HomeTile.PARTNER, result[0])
        assertEquals(HomeTile.SLEEP, result[1])
        assertEquals(HomeTile.entries.toSet(), result.toSet())
        val appended = result.drop(2)
        val expectedAppended = HomeTile.DEFAULT_ORDER.filter { it != HomeTile.PARTNER && it != HomeTile.SLEEP }
        assertEquals(expectedAppended, appended)
    }

    @Test
    fun `default order includes the diaper tile`() {
        assert(HomeTile.DIAPER in HomeTile.deserialize(null))
    }

    @Test
    fun `reconcile appends diaper to a pre-diaper stored order`() {
        val result = HomeTile.reconcile(listOf("SLEEP", "BREASTFEEDING"))
        assert(HomeTile.DIAPER in result.drop(2))
    }

    @Test
    fun `default order includes the doctor visit tile after vaccine`() {
        val order = HomeTile.deserialize(null)
        assert(HomeTile.DOCTOR_VISIT in order)
        assertEquals(order.indexOf(HomeTile.VACCINE) + 1, order.indexOf(HomeTile.DOCTOR_VISIT))
    }

    @Test
    fun `reconcile appends doctor visit to a pre-doctor-visit stored order`() {
        val result = HomeTile.reconcile(listOf("SLEEP", "BREASTFEEDING"))
        assert(HomeTile.DOCTOR_VISIT in result.drop(2))
    }

    @Test
    fun `reconcile deduplicates a repeated stored name`() {
        val result = HomeTile.deserialize("SLEEP,SLEEP,BREASTFEEDING")
        assertEquals(1, result.count { it == HomeTile.SLEEP })
        assertEquals(HomeTile.entries.toSet(), result.toSet())
        assertEquals(HomeTile.DEFAULT_ORDER.size, result.size)
    }
}
