package com.babytracker.sharing.usecase

import com.babytracker.domain.model.SleepType
import com.babytracker.sharing.domain.model.SleepOp
import com.babytracker.sharing.domain.model.SleepOpAction
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Partner edits one of its OWN sessions. The UI only invokes this for a session whose snapshot
 * `startedBy == PARTNER`; the primary also drops OWNER-targeted updates as defense-in-depth.
 * A null [endTime] keeps the session in progress.
 */
class UpdatePartnerSleepUseCase @Inject constructor(
    private val submitSleepOp: SubmitSleepOpUseCase,
    private val now: () -> Instant = Instant::now,
) {
    suspend operator fun invoke(
        clientId: String,
        startTime: Instant,
        endTime: Instant?,
        sleepType: SleepType,
        notes: String?,
    ) {
        require(clientId.isNotBlank()) { "clientId is required" }
        require(!startTime.isAfter(now())) { "Sleep start cannot be in the future" }
        if (endTime != null) {
            require(!endTime.isAfter(now())) { "Sleep end cannot be in the future" }
            require(endTime.isAfter(startTime)) { "Sleep end must be after start" }
        }
        requireValidNotes(notes)
        submitSleepOp { authorUid ->
            SleepOp(
                opId = UUID.randomUUID().toString(),
                action = SleepOpAction.UPDATE,
                entryClientId = clientId,
                authorUid = authorUid,
                createdAtMs = now().toEpochMilli(),
                startTimeMs = startTime.toEpochMilli(),
                endTimeMs = endTime?.toEpochMilli(),
                sleepType = sleepType.name,
                notes = notes,
            )
        }
    }
}
