package com.babytracker.domain.sleep.eval

data class AnchorScore(
    val errorMillis: Long,
    val inWindow: Boolean,
    val missedWindow: Boolean,
)
