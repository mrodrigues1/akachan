package com.babytracker.domain.sleep.feature

data class EvidenceQuality(
    val lastWakeRecencyMillis: Long?,
    val isFresh: Boolean,
    val completedIntervalCount: Int,
    val localDayCoverage: Int,
    val isLocalDayCoverageSufficient: Boolean,
    val wakeIntervalIqrMillis: Long?,
    val invalidRecordRate: Float,
    val hasSufficientZoneIndependentEvidence: Boolean,
)
