package com.babytracker.domain.model

/**
 * The WHO six gross-motor milestones, each carrying its typical window of
 * achievement (the 1st–99th percentile month range from the WHO Motor
 * Development Study). The window is informational only — it is shown to parents,
 * never used to flag a milestone as "late".
 */
enum class Milestone(
    val label: String,
    val windowStartMonths: Double,
    val windowEndMonths: Double,
) {
    SITTING_WITHOUT_SUPPORT("Sitting without support", 3.8, 9.2),
    HANDS_AND_KNEES_CRAWLING("Hands-and-knees crawling", 5.2, 13.5),
    STANDING_WITH_ASSISTANCE("Standing with assistance", 4.8, 11.4),
    WALKING_WITH_ASSISTANCE("Walking with assistance", 5.9, 13.7),
    STANDING_ALONE("Standing alone", 6.9, 16.9),
    WALKING_ALONE("Walking alone", 8.2, 17.6),
}
