package com.babytracker.ui.home

import com.babytracker.domain.model.HomeTile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ReorderByKeyTest {

    private val fullOrder = HomeTile.DEFAULT_ORDER

    @Test
    fun sameKey_returnsIdenticalList() {
        val result = reorderByKey(fullOrder, fullOrder, HomeTile.SLEEP, HomeTile.SLEEP)
        assertEquals(fullOrder, result)
    }

    @Test
    fun moveTileDown_insertsAtTargetSlotAndPreservesAll() {
        // All tiles visible: move BREASTFEEDING (index 0) down to SLEEP's slot (index 1).
        val result = reorderByKey(fullOrder, fullOrder, HomeTile.BREASTFEEDING, HomeTile.SLEEP)
        assertEquals(fullOrder.size, result.size)
        assertEquals(fullOrder.toSet(), result.toSet())
        assertEquals(HomeTile.SLEEP, result[0])
        assertEquals(HomeTile.BREASTFEEDING, result[1])
    }

    @Test
    fun moveTileUp_insertsAtTargetSlotAndPreservesAll() {
        // All tiles visible: move PUMPING (index 2) up to BREASTFEEDING's slot (index 0).
        val breastfeedingIdx = fullOrder.indexOf(HomeTile.BREASTFEEDING)
        val pumpingIdx = fullOrder.indexOf(HomeTile.PUMPING)
        val result = reorderByKey(fullOrder, fullOrder, HomeTile.PUMPING, HomeTile.BREASTFEEDING)
        assertEquals(fullOrder.size, result.size)
        assertEquals(fullOrder.toSet(), result.toSet())
        // PUMPING should now be at BREASTFEEDING's original index.
        assertEquals(HomeTile.PUMPING, result[breastfeedingIdx])
        // BREASTFEEDING should have shifted right past the removed PUMPING.
        val expectedBreastfeedingIdx = if (pumpingIdx > breastfeedingIdx) breastfeedingIdx + 1 else breastfeedingIdx
        assertEquals(HomeTile.BREASTFEEDING, result[expectedBreastfeedingIdx])
    }

    @Test
    fun unknownFromKey_returnsOrderUnchanged() {
        // PARTNER is hidden (not in the visible list), so a drag can never reference it as a source.
        val visible = fullOrder.filter { it != HomeTile.PARTNER }
        val result = reorderByKey(fullOrder, visible, HomeTile.PARTNER, HomeTile.SLEEP)
        assertEquals(fullOrder, result)
    }

    @Test
    fun hiddenTileBetweenVisibleTiles_keepsAbsolutePosition() {
        // SLEEP_PREDICTION (full index 6) is hidden, sitting between FEEDING_HISTORY (5) and TIP (7).
        val hidden = HomeTile.SLEEP_PREDICTION
        val hiddenIdx = fullOrder.indexOf(hidden)
        val visible = fullOrder.filter { it != hidden }

        // Drag a visible tile (FEEDING_HISTORY) across the hidden one onto TIP.
        val result = reorderByKey(fullOrder, visible, HomeTile.FEEDING_HISTORY, HomeTile.TIP)

        // The hidden tile must stay at its exact absolute index — it was not on screen during the drag.
        assertEquals(hidden, result[hiddenIdx])
        assertEquals(fullOrder.size, result.size)
        assertEquals(fullOrder.toSet(), result.toSet())
        // The visible sequence reflects the requested move: FEEDING_HISTORY now follows TIP.
        val expectedVisible = listOf(
            HomeTile.BREASTFEEDING,
            HomeTile.SLEEP,
            HomeTile.PUMPING,
            HomeTile.INVENTORY,
            HomeTile.BOTTLE_FEED,
            HomeTile.GROWTH,
            HomeTile.MILESTONES,
            HomeTile.TRENDS,
            HomeTile.TIP,
            HomeTile.FEEDING_HISTORY,
            HomeTile.PARTNER,
        )
        assertEquals(expectedVisible, result.filter { it != hidden })
    }
}
