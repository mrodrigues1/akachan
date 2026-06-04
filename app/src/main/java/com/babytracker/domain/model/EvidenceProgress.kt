package com.babytracker.domain.model

data class EvidenceProgress(
    val completedIntervals: Int,
    val requiredIntervals: Int,
    val localDays: Int,
    val requiredLocalDays: Int,
    val hint: String,
)
