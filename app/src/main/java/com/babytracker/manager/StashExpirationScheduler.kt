package com.babytracker.manager

/**
 * Schedules / cancels the daily milk-stash expiration notification alarm.
 */
interface StashExpirationScheduler {
    /** Schedule (or reschedule) the daily alarm at [timeMinuteOfDay] (0..1439, minute-of-day). */
    fun scheduleDaily(timeMinuteOfDay: Int)

    /** Cancel any scheduled daily alarm. */
    fun cancel()
}
