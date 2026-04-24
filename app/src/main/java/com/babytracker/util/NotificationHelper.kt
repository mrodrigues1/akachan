package com.babytracker.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import com.babytracker.MainActivity
import com.babytracker.R
import com.babytracker.receiver.BreastfeedingActionReceiver
import com.babytracker.receiver.SleepActionReceiver
import com.babytracker.ui.theme.Blue700
import com.babytracker.ui.theme.Pink700
import com.babytracker.ui.theme.PrimaryPinkDark
import com.babytracker.ui.theme.SecondaryBlueDark
import com.babytracker.ui.theme.WarningAmber
import com.babytracker.ui.theme.WarningAmberDark
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object NotificationHelper {
    const val BREASTFEEDING_CHANNEL_ID = "breastfeeding_notifications"
    const val SLEEP_CHANNEL_ID = "sleep_notifications"
    const val BREASTFEEDING_NOTIFICATION_ID = 1001
    const val SWITCH_SIDE_NOTIFICATION_ID = 1002
    const val SLEEP_NOTIFICATION_ID = 1003
    const val BREASTFEEDING_ACTIVE_NOTIFICATION_ID = 1004
    private const val RC_MAIN_TAP = 0
    private const val RC_SWITCH_NOW = 2001
    private const val RC_BF_DISMISS = 2002
    private const val RC_STOP_SESSION = 2003
    private const val RC_KEEP_GOING = 2004
    private const val RC_STOP_SLEEP = 2005
    private const val RC_STOP_BF_ACTIVE = 2006
    private const val RC_REFRESH_BF_ACTIVE = 2007
    private const val RC_PAUSE_BF_ACTIVE = 2008
    private const val RC_RESUME_BF_ACTIVE = 2009
    private const val TAG = "NotificationHelper"
    private const val SECONDS_PER_MINUTE = 60
    private const val ACTIVE_REFRESH_INTERVAL_MS = 5_000L

    private fun resolveAccent(context: Context, light: Color, dark: Color): Int {
        val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDark = nightMask == Configuration.UI_MODE_NIGHT_YES
        return (if (isDark) dark else light).toArgb()
    }

    private fun NotificationCompat.Builder.applyDesignSystem(
        accentColor: Int,
        smallIconRes: Int
    ): NotificationCompat.Builder = this
        .setSmallIcon(smallIconRes)
        .setColor(accentColor)
        .setColorized(false)
        .setOnlyAlertOnce(true)

    private fun buildProgressBigView(
        context: Context,
        layoutRes: Int,
        title: String,
        body: String,
        progress: Int,
        maxProgress: Int,
        progressText: String,
        chronometerBaseElapsedMs: Long? = null,
        chronometerRunning: Boolean = true,
        showProgress: Boolean = true
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
            false
        )

        if (chronometerBaseElapsedMs != null) {
            setViewVisibility(R.id.notification_timer, View.VISIBLE)
            setChronometer(R.id.notification_timer, chronometerBaseElapsedMs, null, chronometerRunning)
        } else {
            setViewVisibility(R.id.notification_timer, View.GONE)
        }
    }

    private fun formatDurationCompact(totalSeconds: Int): String {
        val safeSeconds = totalSeconds.coerceAtLeast(0)
        val minutes = safeSeconds / SECONDS_PER_MINUTE
        val seconds = safeSeconds % SECONDS_PER_MINUTE
        return when {
            minutes == 0 -> "${seconds}s"
            seconds == 0 -> "${minutes}m"
            else -> "${minutes}m ${seconds}s"
        }
    }

    private fun formatMaxTimeLabel(maxMinutes: Int): String =
        "${maxMinutes.coerceAtLeast(0)} min"

    private fun activeProgressText(maxTotalMinutes: Int): String =
        formatMaxTimeLabel(maxTotalMinutes)

    private fun limitProgressText(maxTotalMinutes: Int): String =
        formatMaxTimeLabel(maxTotalMinutes)

    private fun activeElapsedSeconds(sessionStartEpochMs: Long, pausedDurationMs: Long): Int =
        ((System.currentTimeMillis() - sessionStartEpochMs - pausedDurationMs).coerceAtLeast(0L) / 1000L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()

    private fun pausedElapsedSeconds(
        sessionStartEpochMs: Long,
        pausedDurationMs: Long,
        pausedAtEpochMs: Long
    ): Int =
        ((pausedAtEpochMs - sessionStartEpochMs - pausedDurationMs).coerceAtLeast(0L) / 1000L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()

    fun createBreastfeedingNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BREASTFEEDING_CHANNEL_ID,
                "Breastfeeding Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "Notifications for breastfeeding session timers" }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
            Log.d(TAG, "Channel created: $BREASTFEEDING_CHANNEL_ID")
        }
    }

    fun createSleepNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SLEEP_CHANNEL_ID,
                "Sleep Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Notifications for active sleep sessions" }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
            Log.d(TAG, "Channel created: $SLEEP_CHANNEL_ID")
        }
    }

    fun showSwitchSide(
        context: Context,
        sessionId: Long,
        currentSide: String,
        elapsedMinutes: Int,
        maxPerBreastMinutes: Int,
        maxTotalMinutes: Int,
        richEnabled: Boolean
    ) {
        val otherSide = if (currentSide == "LEFT") "right" else "left"
        val sideLabel = if (currentSide == "LEFT") "Left" else "Right"
        val title = "\uD83C\uDF7C Time to switch sides"
        val body = "$sideLabel breast: $elapsedMinutes min \u00B7 Switch to the $otherSide"
        val tapPi = mainActivityPendingIntent(context)
        val accent = resolveAccent(context, Pink700, PrimaryPinkDark)
        val builder = NotificationCompat.Builder(context, BREASTFEEDING_CHANNEL_ID)
            .applyDesignSystem(accent, R.drawable.ic_notif_breastfeeding)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(tapPi)

        if (richEnabled) {
            val progressText = "$elapsedMinutes / $maxPerBreastMinutes min"
            builder
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomBigContentView(
                    buildProgressBigView(
                        context = context,
                        layoutRes = R.layout.notification_breastfeeding_progress,
                        title = title,
                        body = body,
                        progress = elapsedMinutes,
                        maxProgress = maxPerBreastMinutes,
                        progressText = progressText
                    )
                )
                .addAction(0, "Switch Now", breastfeedingActionPi(context, sessionId, BreastfeedingActionReceiver.ACTION_SWITCH, RC_SWITCH_NOW))
                .addAction(0, "Dismiss", breastfeedingActionPi(context, sessionId, BreastfeedingActionReceiver.ACTION_DISMISS, RC_BF_DISMISS))
        } else {
            builder.setContentText(body)
        }

        context.getSystemService(NotificationManager::class.java)
            .notify(SWITCH_SIDE_NOTIFICATION_ID, builder.build())
        Log.d(TAG, "showSwitchSide posted (rich=$richEnabled)")
    }

    fun showFeedingLimit(
        context: Context,
        sessionId: Long,
        maxTotalMinutes: Int,
        richEnabled: Boolean
    ) {
        val title = "\u23F1 Feeding limit reached"
        val body = "Session has reached $maxTotalMinutes minutes. Consider wrapping up."
        val tapPi = mainActivityPendingIntent(context)
        val accent = resolveAccent(context, WarningAmber, WarningAmberDark)
        val builder = NotificationCompat.Builder(context, BREASTFEEDING_CHANNEL_ID)
            .applyDesignSystem(accent, R.drawable.ic_notif_limit)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(tapPi)

        if (richEnabled) {
            val progressText = limitProgressText(maxTotalMinutes)
            builder
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomBigContentView(
                    buildProgressBigView(
                        context = context,
                        layoutRes = R.layout.notification_warning_progress,
                        title = title,
                        body = body,
                        progress = maxTotalMinutes,
                        maxProgress = maxTotalMinutes,
                        progressText = progressText
                    )
                )
                .addAction(0, "Stop Session", breastfeedingActionPi(context, sessionId, BreastfeedingActionReceiver.ACTION_STOP, RC_STOP_SESSION))
                .addAction(0, "Keep Going", breastfeedingActionPi(context, sessionId, BreastfeedingActionReceiver.ACTION_KEEP_GOING, RC_KEEP_GOING))
        } else {
            builder.setContentText(body)
        }

        context.getSystemService(NotificationManager::class.java)
            .notify(BREASTFEEDING_NOTIFICATION_ID, builder.build())
        Log.d(TAG, "showFeedingLimit posted (rich=$richEnabled)")
    }

    fun showBreastfeedingActive(
        context: Context,
        sessionId: Long,
        currentSide: String,
        sessionStartEpochMs: Long,
        pausedDurationMs: Long,
        richEnabled: Boolean,
        maxTotalMinutes: Int = 0,
        pausedAtEpochMs: Long? = null
    ) {
        val sideLabel = if (currentSide == "LEFT") "Left" else "Right"
        val isPaused = pausedAtEpochMs != null
        val title = if (isPaused) "\uD83C\uDF7C Feeding session paused" else "\uD83C\uDF7C Feeding session active"
        val body = if (isPaused) "$sideLabel breast \u00B7 Session paused" else "$sideLabel breast \u00B7 Session in progress"
        val tapPi = mainActivityPendingIntent(context)
        val accent = resolveAccent(context, Pink700, PrimaryPinkDark)
        val builder = NotificationCompat.Builder(context, BREASTFEEDING_CHANNEL_ID)
            .applyDesignSystem(accent, R.drawable.ic_notif_breastfeeding)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(tapPi)

        if (richEnabled) {
            val elapsedSeconds = pausedAtEpochMs
                ?.let { pausedElapsedSeconds(sessionStartEpochMs, pausedDurationMs, it) }
                ?: activeElapsedSeconds(sessionStartEpochMs, pausedDurationMs)
            val maxSeconds = maxTotalMinutes * SECONDS_PER_MINUTE
            val progressEnabled = maxSeconds > 0
            val progress = if (progressEnabled) elapsedSeconds else 0
            val maxProgress = if (progressEnabled) maxSeconds else 1
            val progressText = if (progressEnabled) {
                activeProgressText(maxTotalMinutes)
            } else {
                formatDurationCompact(elapsedSeconds)
            }

            builder
                .setUsesChronometer(!isPaused)
                .setShowWhen(!isPaused)
                .setWhen(sessionStartEpochMs + pausedDurationMs)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomBigContentView(
                    buildProgressBigView(
                        context = context,
                        layoutRes = R.layout.notification_breastfeeding_progress,
                        title = title,
                        body = body,
                        progress = progress,
                        maxProgress = maxProgress,
                        progressText = progressText,
                        chronometerBaseElapsedMs = SystemClock.elapsedRealtime() - (elapsedSeconds * 1000L),
                        chronometerRunning = !isPaused,
                        showProgress = progressEnabled
                    )
                )
                .addAction(
                    0,
                    if (isPaused) "Resume" else "Pause",
                    breastfeedingActionPi(
                        context = context,
                        sessionId = sessionId,
                        action = if (isPaused) BreastfeedingActionReceiver.ACTION_RESUME else BreastfeedingActionReceiver.ACTION_PAUSE,
                        requestCode = if (isPaused) RC_RESUME_BF_ACTIVE else RC_PAUSE_BF_ACTIVE
                    )
                )
                .addAction(0, "Stop Session", breastfeedingActionPi(context, sessionId, BreastfeedingActionReceiver.ACTION_STOP, RC_STOP_BF_ACTIVE))

            if (progressEnabled && !isPaused) {
                scheduleBreastfeedingActiveRefresh(context, sessionId)
            }
        } else {
            builder.setContentText(body)
        }

        context.getSystemService(NotificationManager::class.java)
            .notify(BREASTFEEDING_ACTIVE_NOTIFICATION_ID, builder.build())
        Log.d(TAG, "showBreastfeedingActive posted (rich=$richEnabled)")
    }

    fun showSleepActive(
        context: Context,
        sessionId: Long,
        sleepType: String,
        startTimeEpochMs: Long,
        richEnabled: Boolean
    ) {
        val typeLabel = if (sleepType == "NIGHT_SLEEP") "Night sleep" else "Nap"
        val startFormatted = java.time.Instant.ofEpochMilli(startTimeEpochMs)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("h:mm a"))
        val title = "\uD83C\uDF19 Sleep session active"
        val body = "$typeLabel \u00B7 started at $startFormatted"
        val tapPi = mainActivityPendingIntent(context)
        val accent = resolveAccent(context, Blue700, SecondaryBlueDark)
        val builder = NotificationCompat.Builder(context, SLEEP_CHANNEL_ID)
            .applyDesignSystem(accent, R.drawable.ic_notif_sleep)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(false)
            .setOngoing(false)
            .setContentIntent(tapPi)

        if (richEnabled) {
            builder
                .setUsesChronometer(true)
                .setWhen(startTimeEpochMs)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .addAction(0, "Stop Sleep", sleepActionPi(context, sessionId, SleepActionReceiver.ACTION_STOP, RC_STOP_SLEEP))
        } else {
            builder.setContentText(body)
        }

        context.getSystemService(NotificationManager::class.java)
            .notify(SLEEP_NOTIFICATION_ID, builder.build())
        Log.d(TAG, "showSleepActive posted (rich=$richEnabled)")
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        context.getSystemService(NotificationManager::class.java).cancel(notificationId)
        Log.d(TAG, "Notification cancelled (ID: $notificationId)")
    }

    private fun mainActivityPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, RC_MAIN_TAP,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    private fun breastfeedingActionPi(
        context: Context, sessionId: Long, action: String, requestCode: Int
    ): PendingIntent = PendingIntent.getBroadcast(
        context, requestCode,
        Intent(BreastfeedingActionReceiver.ACTION).apply {
            setClass(context, BreastfeedingActionReceiver::class.java)
            putExtra(BreastfeedingActionReceiver.EXTRA_SESSION_ID, sessionId)
            putExtra(BreastfeedingActionReceiver.EXTRA_ACTION, action)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun scheduleBreastfeedingActiveRefresh(context: Context, sessionId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            RC_REFRESH_BF_ACTIVE,
            Intent(BreastfeedingActionReceiver.ACTION).apply {
                setClass(context, BreastfeedingActionReceiver::class.java)
                putExtra(BreastfeedingActionReceiver.EXTRA_SESSION_ID, sessionId)
                putExtra(BreastfeedingActionReceiver.EXTRA_ACTION, BreastfeedingActionReceiver.ACTION_REFRESH_ACTIVE)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + ACTIVE_REFRESH_INTERVAL_MS,
            pendingIntent
        )
    }

    private fun sleepActionPi(
        context: Context, sessionId: Long, action: String, requestCode: Int
    ): PendingIntent = PendingIntent.getBroadcast(
        context, requestCode,
        Intent(SleepActionReceiver.ACTION).apply {
            setClass(context, SleepActionReceiver::class.java)
            putExtra(SleepActionReceiver.EXTRA_SESSION_ID, sessionId)
            putExtra(SleepActionReceiver.EXTRA_ACTION, action)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
