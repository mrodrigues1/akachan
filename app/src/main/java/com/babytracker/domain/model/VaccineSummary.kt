package com.babytracker.domain.model

data class VaccineSummary(
    val nextUpcoming: VaccineRecord? = null,
    val upcomingCount: Int = 0,
    val overdueCount: Int = 0,
    val administeredCount: Int = 0,
    val lastAdministered: VaccineRecord? = null,
) {
    val hasAny: Boolean get() = nextUpcoming != null || administeredCount > 0 || overdueCount > 0
}
