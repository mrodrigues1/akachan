package com.babytracker.widget

/**
 * Enqueues an out-of-band widget refresh so the partner widget populates promptly (on connect, or
 * when the render path sees an empty cache) rather than waiting for the 15-min periodic worker.
 */
interface WidgetRefreshScheduler {
    fun scheduleImmediateRefresh()
}
