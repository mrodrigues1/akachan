package com.babytracker.domain.model

import java.time.Instant

data class VisitQuestion(
    val id: Long = 0,
    val text: String,
    val answered: Boolean = false,
    val visitId: Long? = null,
    val createdAt: Instant,
)
