package com.babytracker.domain.growth

import com.babytracker.domain.model.BabySex
import com.babytracker.domain.model.GrowthType

/**
 * Supplies WHO LMS reference tables for a given metric and sex. WHO growth
 * standards are sex-specific, so [BabySex.UNSPECIFIED] has no table and returns
 * an empty list (callers then suppress percentile ranks/curves).
 */
interface WhoReferenceData {
    suspend fun lmsTable(type: GrowthType, sex: BabySex): List<LmsPoint>
}
