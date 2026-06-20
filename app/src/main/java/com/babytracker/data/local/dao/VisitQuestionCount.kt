package com.babytracker.data.local.dao

/** Projection for the per-visit attached-question count used by the history screen. */
data class VisitQuestionCount(val visitId: Long, val count: Int)
