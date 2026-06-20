package com.babytracker.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

const val REMINDER_HOUR_OF_DAY = 9

/**
 * Reminder trigger for a "lead-days before a target date" alarm: [reminderHour]:00 local,
 * [leadDays] before [targetMs]. If that lead window has already passed, fall back to the NEXT
 * [reminderHour]:00 strictly after [nowMs] — but never at/after the target instant (no immediate
 * late-night ping, no post-target ping). Returns null when [targetMs] is already past or no window
 * remains before it. Pure + testable without AlarmManager. Shared by the date-based reminder
 * managers (vaccine, doctor visit).
 */
fun computeReminderTriggerAtMs(
    targetMs: Long,
    leadDays: Int,
    nowMs: Long,
    zone: ZoneId,
    reminderHour: Int = REMINDER_HOUR_OF_DAY,
): Long? {
    if (targetMs <= nowMs) return null
    fun reminderTimeOn(date: LocalDate): Long =
        date.atTime(reminderHour, 0).atZone(zone).toInstant().toEpochMilli()
    val targetDate = Instant.ofEpochMilli(targetMs).atZone(zone).toLocalDate()
    val leadTrigger = reminderTimeOn(targetDate.minusDays(leadDays.toLong()))
    val candidate = if (leadTrigger > nowMs) {
        leadTrigger
    } else {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val todayReminder = reminderTimeOn(today)
        if (todayReminder > nowMs) todayReminder else reminderTimeOn(today.plusDays(1))
    }
    return candidate.takeIf { it < targetMs }
}
