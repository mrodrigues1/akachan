package com.babytracker.manager

import com.babytracker.domain.model.VaccineRecord

interface VaccineReminderScheduler {
    /**
     * Re-arms the reminder for [record]. Implementations MUST first cancel any existing reminder
     * for [record].id, then arm a new one only if the record is SCHEDULED with a future date.
     * Calling this for an administered or past record therefore just clears a now-stale alarm —
     * this is what lets [EditVaccineRecordUseCase] call schedule() unconditionally without leaking
     * reminders when a scheduled vaccine is edited to administered.
     */
    suspend fun schedule(record: VaccineRecord)

    /** Cancels any pending reminder for this record id. */
    fun cancel(recordId: Long)

    /** Cancels all and re-arms from currently-scheduled future records (boot / settings change / import). */
    suspend fun rescheduleAll()
}
