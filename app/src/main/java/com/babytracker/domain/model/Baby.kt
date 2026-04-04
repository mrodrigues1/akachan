package com.babytracker.domain.model

import java.time.Instant

data class Baby(
    val name: String,
    val birthDate: Instant,
    val allergies: List<AllergyType> = emptyList()
)
