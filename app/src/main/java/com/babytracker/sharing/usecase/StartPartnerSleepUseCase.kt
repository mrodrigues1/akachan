package com.babytracker.sharing.usecase

import com.babytracker.domain.model.SleepType
import com.babytracker.sharing.domain.model.SleepOp
import com.babytracker.sharing.domain.model.SleepOpAction
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/** Partner starts a nap / night sleep. Returns the new entryClientId so the UI can track its op. */
class StartPartnerSleepUseCase @Inject constructor(
    private val submitSleepOp: SubmitSleepOpUseCase,
    private val now: () -> Instant = Instant::now,
) {
    suspend operator fun invoke(sleepType: SleepType): String {
        val entryClientId = UUID.randomUUID().toString()
        val nowMs = now().toEpochMilli()
        submitSleepOp { authorUid ->
            SleepOp(
                opId = UUID.randomUUID().toString(),
                action = SleepOpAction.START,
                entryClientId = entryClientId,
                authorUid = authorUid,
                createdAtMs = nowMs,
                startTimeMs = nowMs,
                sleepType = sleepType,
            )
        }
        return entryClientId
    }
}
