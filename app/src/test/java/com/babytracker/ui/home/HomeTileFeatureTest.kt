package com.babytracker.ui.home

import com.babytracker.domain.model.AppFeature
import com.babytracker.domain.model.HomeTile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HomeTileFeatureTest {

    @Test
    fun `tracker tiles map to their feature`() {
        assertEquals(AppFeature.BREASTFEEDING, HomeTile.BREASTFEEDING.requiredFeature())
        assertEquals(AppFeature.BOTTLE_FEED, HomeTile.BOTTLE_FEED.requiredFeature())
        assertEquals(AppFeature.PUMPING, HomeTile.PUMPING.requiredFeature())
        assertEquals(AppFeature.INVENTORY, HomeTile.INVENTORY.requiredFeature())
        assertEquals(AppFeature.SLEEP, HomeTile.SLEEP.requiredFeature())
        assertEquals(AppFeature.SLEEP, HomeTile.SLEEP_PREDICTION.requiredFeature())
        assertEquals(AppFeature.DIAPERS, HomeTile.DIAPER.requiredFeature())
        assertEquals(AppFeature.GROWTH, HomeTile.GROWTH.requiredFeature())
        assertEquals(AppFeature.MILESTONES, HomeTile.MILESTONES.requiredFeature())
        assertEquals(AppFeature.BREASTFEEDING, HomeTile.TIP.requiredFeature())
    }

    @Test
    fun `derived and always-on tiles have no single required feature`() {
        assertNull(HomeTile.FEEDING_HISTORY.requiredFeature())
        assertNull(HomeTile.TRENDS.requiredFeature())
        assertNull(HomeTile.PARTNER.requiredFeature())
    }
}
