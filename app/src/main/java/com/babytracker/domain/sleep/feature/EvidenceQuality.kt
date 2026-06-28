package com.babytracker.domain.sleep.feature

data class EvidenceQuality(
    val isFresh: Boolean,
    val completedIntervalCount: Int,
    val localDayCoverage: Int,
    val isLocalDayCoverageSufficient: Boolean,
    val hasSufficientZoneIndependentEvidence: Boolean,
    val hasQualifiedTimezoneProvenance: Boolean = false,
)
