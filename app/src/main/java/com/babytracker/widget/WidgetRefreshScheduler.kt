package com.babytracker.widget

/**
 * Enqueues an out-of-band immediate widget refresh, bypassing the 15-minute periodic schedule.
 * Used both for cache-miss auto-refresh in partner mode and for user-initiated manual refresh.
 */
interface WidgetRefreshScheduler {
    fun scheduleImmediateRefresh()
}
