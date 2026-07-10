package com.babytracker.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.os.Build
import android.util.Log

/**
 * Schedules [pi] to fire at [triggerAtMs] as an exact, doze-piercing alarm via
 * [AlarmManager.setExactAndAllowWhileIdle], falling back to the inexact
 * [AlarmManager.setAndAllowWhileIdle] when exact alarms aren't available. Below API 31 exact alarms
 * always require no runtime permission; from API 31 on, [AlarmManager.canScheduleExactAlarms] gates
 * it, and the permission can also be revoked between that check and the call, which throws a
 * [SecurityException] this also falls back on. [tag] is used for the fallback log line so each caller
 * keeps its own logcat tag. Shared by every alarm-based notification scheduler so this version-gating
 * and fallback logic lives in one place instead of being copy-pasted per caller.
 */
fun AlarmManager.setExactWithFallback(type: Int, triggerAtMs: Long, pi: PendingIntent, tag: String) {
    val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || canScheduleExactAlarms()
    try {
        if (canExact) {
            setExactAndAllowWhileIdle(type, triggerAtMs, pi)
        } else {
            setAndAllowWhileIdle(type, triggerAtMs, pi)
        }
    } catch (e: SecurityException) {
        Log.w(tag, "SCHEDULE_EXACT_ALARM revoked; falling back to inexact", e)
        setAndAllowWhileIdle(type, triggerAtMs, pi)
    }
}
