package com.babytracker.sharing.usecase

import com.babytracker.sharing.domain.model.SleepOp
import com.babytracker.sharing.domain.model.SleepOpAction
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Partner stops the active session. STOP is ungated and targets the [activeClientId] the partner
 * sees in the snapshot (not "whatever is active"), so a stale snapshot can't stop a newer session.
 */
class StopPartnerSleepUseCase @Inject constructor(
    private val submitSleepOp: SubmitSleepOpUseCase,
    private val now: () -> Instant = Instant::now,
) {
    suspend operator fun invoke(activeClientId: String) {
        require(activeClientId.isNotBlank()) { "activeClientId is required" }
        val nowMs = now().toEpochMilli()
        submitSleepOp { authorUid ->
            SleepOp(
                opId = UUID.randomUUID().toString(),
                action = SleepOpAction.STOP,
                entryClientId = activeClientId,
                authorUid = authorUid,
                createdAtMs = nowMs,
                endTimeMs = nowMs,
            )
        }
    }
}
