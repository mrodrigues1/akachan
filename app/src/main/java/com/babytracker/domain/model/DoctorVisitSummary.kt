package com.babytracker.domain.model

data class DoctorVisitSummary(
    val nextUpcoming: DoctorVisit? = null,
    val lastPast: DoctorVisit? = null,
    val openQuestionCount: Int = 0,
) {
    val hasAny: Boolean
        get() = nextUpcoming != null || lastPast != null || openQuestionCount > 0
}
