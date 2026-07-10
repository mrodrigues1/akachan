package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.RecommendationLifecycle
import com.babytracker.domain.model.RecommendationOutcome
import com.babytracker.domain.repository.SleepRecommendationRepository
import java.time.Instant
import javax.inject.Inject

/**
 * The coordinator's recommendation lifecycle/feedback writes, grouped behind named operations
 * instead of a raw repository — this class exists specifically so the coordinator's constructor
 * does not re-grow the leaky, repository-exposing `SleepRecommendationUseCases` aggregator it
 * replaces (that aggregator's `val repository` let callers invoke *any* repository method; here
 * every write is a named, single-purpose method).
 *
 * [invoke] (supersede) is the reason this class exists as a *use case*: abandoning a recommendation
 * marks it SUPERSEDED and records a paired SUPERSEDED feedback row. The two writes always happen
 * together whenever a recommendation stops being current (feature disabled, sleep-window anchor
 * changed, or a Window prediction resolves to something else) — genuine multi-step behavior that
 * earns its place per ADR-0001.
 *
 * [markScheduled] and [recordFeedback] are plain single-write delegations with no branching of
 * their own — by ADR-0001 they would normally be direct repository/use-case calls rather than a
 * use case of their own — but living here (sharing [repository] and the feedback use case this
 * class already depends on for [invoke]) keeps the coordinator's own dependency list from growing
 * with single-call collaborators, without ever leaking the repository itself.
 */
class SupersedeSleepRecommendationUseCase @Inject constructor(
    private val repository: SleepRecommendationRepository,
    private val createFeedback: CreateSleepRecommendationFeedbackUseCase,
) {
    suspend operator fun invoke(recommendationId: Long) {
        repository.updateLifecycle(recommendationId, RecommendationLifecycle.SUPERSEDED)
        createFeedback(recommendationId, RecommendationOutcome.SUPERSEDED)
    }

    suspend fun markScheduled(recommendationId: Long) {
        repository.updateLifecycle(recommendationId, RecommendationLifecycle.SCHEDULED)
    }

    suspend fun recordFeedback(
        recommendationId: Long,
        outcome: RecommendationOutcome,
        actualSleepRecordId: Long? = null,
        sleepStartTime: Instant? = null,
        windowBestEstimate: Instant? = null,
    ): Long = createFeedback(recommendationId, outcome, actualSleepRecordId, sleepStartTime, windowBestEstimate)
}
