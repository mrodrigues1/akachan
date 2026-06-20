package com.babytracker.util

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import com.babytracker.R

private const val SECONDS_PER_MINUTE = 60

internal data class ActiveProgress(
    val current: Int,
    val max: Int,
    val label: String,
    val isEnabled: Boolean,
)

internal data class ActiveNotificationTiming(
    val elapsedMs: Long,
    val elapsedSeconds: Int,
    val chronometerBaseElapsedMs: Long,
)

internal data class CollapsedTimerContent(
    val titleSuffix: String,
    val chronometerBaseElapsedMs: Long,
    val chronometerRunning: Boolean,
)

internal data class ActiveNotificationContent(
    val context: Context,
    val sessionId: Long,
    val sessionStartEpochMs: Long,
    val pausedDurationMs: Long,
    val pausedAtEpochMs: Long?,
    val maxTotalMinutes: Int,
    val title: String,
    val body: String,
    val canSwitchSides: Boolean = true,
)

internal fun buildProgressBigView(
    context: Context,
    layoutRes: Int,
    title: String,
    body: String,
    progress: Int,
    maxProgress: Int,
    progressText: String,
    chronometerBaseElapsedMs: Long? = null,
    chronometerRunning: Boolean = true,
    showProgress: Boolean = true,
): RemoteViews = RemoteViews(context.packageName, layoutRes).apply {
    val safeMax = maxProgress.coerceAtLeast(1)

    setTextViewText(R.id.notification_title, title)
    setTextViewText(R.id.notification_body, body)
    setTextViewText(R.id.notification_progress_label, progressText)
    setViewVisibility(R.id.notification_progress, if (showProgress) View.VISIBLE else View.GONE)
    setViewVisibility(R.id.notification_progress_label, if (showProgress) View.VISIBLE else View.GONE)
    setProgressBar(
        R.id.notification_progress,
        safeMax,
        progress.coerceIn(0, safeMax),
        false,
    )

    if (chronometerBaseElapsedMs != null) {
        setImageViewResource(R.id.notification_title_icon, R.drawable.ic_breastfeeding_section)
        setViewVisibility(R.id.notification_timer, View.VISIBLE)
        setChronometer(R.id.notification_timer, chronometerBaseElapsedMs, null, chronometerRunning)
    } else {
        setImageViewResource(R.id.notification_title_icon, R.drawable.ic_breastfeeding_section)
        setViewVisibility(R.id.notification_timer, View.GONE)
    }
}

internal fun buildProgressOnlyBigView(
    context: Context,
    layoutRes: Int,
    title: String,
    body: String,
    progress: Int,
    maxProgress: Int,
    progressText: String,
): RemoteViews = RemoteViews(context.packageName, layoutRes).apply {
    val safeMax = maxProgress.coerceAtLeast(1)

    setTextViewText(R.id.notification_title, title)
    setTextViewText(R.id.notification_body, body)
    setTextViewText(R.id.notification_progress_label, progressText)
    setProgressBar(
        R.id.notification_progress,
        safeMax,
        progress.coerceIn(0, safeMax),
        false,
    )
}

internal fun buildChronometerBigView(
    context: Context,
    layoutRes: Int,
    title: String,
    body: String,
    chronometerBaseElapsedMs: Long,
    chronometerRunning: Boolean,
    titleIconRes: Int? = null,
): RemoteViews = RemoteViews(context.packageName, layoutRes).apply {
    titleIconRes?.let { setImageViewResource(R.id.notification_title_icon, it) }
    setTextViewText(R.id.notification_title, title)
    setTextViewText(R.id.notification_body, body)
    setChronometer(R.id.notification_timer, chronometerBaseElapsedMs, null, chronometerRunning)
}

internal fun buildCollapsedView(
    context: Context,
    layoutRes: Int,
    title: String,
    body: String,
    progress: Int,
    maxProgress: Int,
    showProgress: Boolean,
    titleIconRes: Int? = null,
): RemoteViews = RemoteViews(context.packageName, layoutRes).apply {
    val safeMax = maxProgress.coerceAtLeast(1)
    titleIconRes?.let { setImageViewResource(R.id.notification_title_icon, it) }
    setTextViewText(R.id.notification_title, title)
    setTextViewText(R.id.notification_body, body)
    setViewVisibility(R.id.notification_progress, if (showProgress) View.VISIBLE else View.GONE)
    if (showProgress) {
        setProgressBar(
            R.id.notification_progress,
            safeMax,
            progress.coerceIn(0, safeMax),
            false,
        )
    }
}

internal fun buildFeedingActiveCollapsedView(
    context: Context,
    layoutRes: Int,
    progress: Int,
    maxProgress: Int,
    showProgress: Boolean,
    body: String,
    timer: CollapsedTimerContent,
): RemoteViews = RemoteViews(context.packageName, layoutRes).apply {
    val safeMax = maxProgress.coerceAtLeast(1)
    setImageViewResource(R.id.notification_title_icon, R.drawable.ic_breastfeeding_section)
    setTextViewText(R.id.notification_title, timer.titleSuffix)
    setViewVisibility(R.id.notification_collapsed_timer, View.VISIBLE)
    setChronometer(R.id.notification_collapsed_timer, timer.chronometerBaseElapsedMs, null, timer.chronometerRunning)
    setTextViewText(R.id.notification_body, body)
    setViewVisibility(R.id.notification_progress, if (showProgress) View.VISIBLE else View.GONE)
    if (showProgress) {
        setProgressBar(R.id.notification_progress, safeMax, progress.coerceIn(0, safeMax), false)
    }
}

internal fun formatDurationCompact(totalSeconds: Int): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0)
    val minutes = safeSeconds / SECONDS_PER_MINUTE
    val seconds = safeSeconds % SECONDS_PER_MINUTE
    return when {
        minutes == 0 -> "${seconds}s"
        seconds == 0 -> "${minutes} min"
        else -> "${minutes} min ${seconds}s"
    }
}

internal fun formatMaxTimeLabel(maxMinutes: Int): String =
    "${maxMinutes.coerceAtLeast(0)} min"

internal fun activeProgressText(maxTotalMinutes: Int): String =
    formatMaxTimeLabel(maxTotalMinutes)

internal fun limitProgressText(maxTotalMinutes: Int): String =
    "${formatMaxTimeLabel(maxTotalMinutes)} reached"

internal fun elapsedSeconds(elapsedMs: Long): Int =
    (elapsedMs.coerceAtLeast(0L) / 1000L)
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()

internal fun activeNotificationTiming(
    sessionStartEpochMs: Long,
    pausedDurationMs: Long,
    pausedAtEpochMs: Long?,
    nowEpochMs: Long = System.currentTimeMillis(),
    elapsedRealtimeMs: Long = SystemClock.elapsedRealtime(),
): ActiveNotificationTiming {
    val elapsedMs = ((pausedAtEpochMs ?: nowEpochMs) - sessionStartEpochMs - pausedDurationMs)
        .coerceAtLeast(0L)
    return ActiveNotificationTiming(
        elapsedMs = elapsedMs,
        elapsedSeconds = elapsedSeconds(elapsedMs),
        chronometerBaseElapsedMs = elapsedRealtimeMs - elapsedMs,
    )
}
