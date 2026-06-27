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
import com.babytracker.navigation.Routes
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
    const val BREASTFEEDING_GROUP_SUMMARY_NOTIFICATION_ID = 1005
    const val NAP_REMINDER_NOTIFICATION_ID = 1007
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
    private const val RC_SWITCH_BF_ACTIVE = 2010
    private const val RC_NAP_REMINDER_TAP = 3002
    private const val TAG = "NotificationHelper"
    private const val SECONDS_PER_MINUTE = 60
    private const val ACTIVE_REFRESH_INTERVAL_MS = 30_000L
    const val BREASTFEEDING_GROUP_KEY = "com.babytracker.notifications.breastfeeding"
    const val SLEEP_GROUP_KEY = "com.babytracker.notifications.sleep"

    internal fun resolveAccent(context: Context, light: Color, dark: Color): Int {
        val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDark = nightMask == Configuration.UI_MODE_NIGHT_YES
        return (if (isDark) dark else light).toArgb()
    }

    private fun NotificationCompat.Builder.applyDesignSystem(
        accentColor: Int,
        smallIconRes: Int,
        visibility: Int = NotificationCompat.VISIBILITY_PRIVATE
    ): NotificationCompat.Builder = this
        .setSmallIcon(smallIconRes)
        .setColor(accentColor)
        .setColorized(false)
        .setOnlyAlertOnce(true)
        .setVisibility(visibility)

    fun createBreastfeedingNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                BREASTFEEDING_CHANNEL_ID,
                context.getString(R.string.notif_channel_breastfeeding_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = context.getString(R.string.notif_channel_breastfeeding_description) }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
            Log.d(TAG, "Channel created: $BREASTFEEDING_CHANNEL_ID")
        }
    }

    fun createSleepNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                SLEEP_CHANNEL_ID,
                context.getString(R.string.notif_channel_sleep_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = context.getString(R.string.notif_channel_sleep_description) }
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
        richEnabled: Boolean
    ) {
        val otherSide = if (currentSide == "LEFT") context.getString(R.string.notif_side_right) else context.getString(R.string.notif_side_left)
        val sideLabel = if (currentSide == "LEFT") context.getString(R.string.notif_side_left) else context.getString(R.string.notif_side_right)
        val title = context.getString(R.string.notif_title_switch_sides)
        val body = context.getString(R.string.notif_body_switch_sides, sideLabel, elapsedMinutes, otherSide)
        val tapPi = mainActivityPendingIntent(context)
        val accent = resolveAccent(context, Pink700, PrimaryPinkDark)
        val builder = NotificationCompat.Builder(context, BREASTFEEDING_CHANNEL_ID)
            .applyDesignSystem(accent, R.drawable.ic_notif_breastfeeding)
            .setContentTitle(title)
            .setContentText(body)
            .setTicker(title)
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setGroup(BREASTFEEDING_GROUP_KEY)
            .setSortKey("2_switch")
            .setContentIntent(tapPi)

        if (richEnabled) {
            builder
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(
                    buildCollapsedView(
                        context = context,
                        layoutRes = R.layout.notification_collapsed_feeding,
                        title = title,
                        body = body,
                        progress = 1,
                        maxProgress = 1,
                        showProgress = false,
                        titleIconRes = R.drawable.ic_breastfeeding_section,
                    )
                )
                .setCustomBigContentView(
                    buildProgressBigView(
                        context = context,
                        layoutRes = R.layout.notification_breastfeeding_progress,
                        title = title,
                        body = body,
                        progress = 0,
                        maxProgress = 1,
                        progressText = "",
                        showProgress = false
                    )
                )
                .addAction(0, context.getString(R.string.notif_action_switch_now), breastfeedingActionPi(context, sessionId, BreastfeedingActionReceiver.ACTION_SWITCH, RC_SWITCH_NOW))
                .addAction(
                    0,
                    context.getString(R.string.notif_action_keep_current_side),
                    breastfeedingActionPi(context, sessionId, BreastfeedingActionReceiver.ACTION_DISMISS, RC_BF_DISMISS)
                )
        } else {
            builder.setContentText(body)
        }

        postBreastfeedingGroupSummary(context)
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
        val title = context.getString(R.string.notif_title_feeding_limit)
        val body = context.getString(R.string.notif_body_feeding_limit, maxTotalMinutes)
        val tapPi = mainActivityPendingIntent(context)
        val accent = resolveAccent(context, WarningAmber, WarningAmberDark)
        val builder = NotificationCompat.Builder(context, BREASTFEEDING_CHANNEL_ID)
            .applyDesignSystem(accent, R.drawable.ic_notif_limit)
            .setContentTitle(title)
            .setContentText(body)
            .setTicker(title)
            .setAutoCancel(true)
            .setOngoing(false)
            .setGroup(BREASTFEEDING_GROUP_KEY)
            .setSortKey("3_limit")
            .setContentIntent(tapPi)

        if (richEnabled) {
            val progressText = limitProgressText(maxTotalMinutes)
            builder
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomContentView(
                    buildCollapsedView(
                        context = context,
                        layoutRes = R.layout.notification_collapsed_warning,
                        title = title,
                        body = body,
                        progress = maxTotalMinutes,
                        maxProgress = maxTotalMinutes,
                        showProgress = true
                    )
                )
                .setCustomBigContentView(
                    buildProgressOnlyBigView(
                        context = context,
                        layoutRes = R.layout.notification_warning_progress,
                        title = title,
                        body = body,
                        progress = maxTotalMinutes,
                        maxProgress = maxTotalMinutes,
                        progressText = progressText
                    )
                )
                .addAction(0, context.getString(R.string.notif_action_stop_feeding), breastfeedingActionPi(context, sessionId, BreastfeedingActionReceiver.ACTION_STOP, RC_STOP_SESSION))
                .addAction(0, context.getString(R.string.notif_action_continue), breastfeedingActionPi(context, sessionId, BreastfeedingActionReceiver.ACTION_KEEP_GOING, RC_KEEP_GOING))
        } else {
            builder.setContentText(body)
        }

        postBreastfeedingGroupSummary(context)
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
        pausedAtEpochMs: Long? = null,
        canSwitchSides: Boolean = true
    ) {
        val sideLabel = if (currentSide == "LEFT") context.getString(R.string.notif_side_left) else context.getString(R.string.notif_side_right)
        val isPaused = pausedAtEpochMs != null
        val title = context.getString(if (isPaused) R.string.notif_title_feeding_paused else R.string.notif_title_feeding_active)
        val body = context.getString(if (isPaused) R.string.notif_body_feeding_paused else R.string.notif_body_feeding_active, sideLabel)
        val tapPi = mainActivityPendingIntent(context)
        val accent = resolveAccent(context, Pink700, PrimaryPinkDark)
        val builder = NotificationCompat.Builder(context, BREASTFEEDING_CHANNEL_ID)
            .applyDesignSystem(accent, R.drawable.ic_notif_breastfeeding)
            .setContentTitle(title)
            .setContentText(body)
            .setTicker(title)
            .setAutoCancel(false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setGroup(BREASTFEEDING_GROUP_KEY)
            .setSortKey("1_active")
            .setContentIntent(tapPi)

        if (richEnabled) {
            builder.applyBreastfeedingActiveRichContent(
                ActiveNotificationContent(
                    context = context,
                    sessionId = sessionId,
                    sessionStartEpochMs = sessionStartEpochMs,
                    pausedDurationMs = pausedDurationMs,
                    pausedAtEpochMs = pausedAtEpochMs,
                    maxTotalMinutes = maxTotalMinutes,
                    title = title,
                    body = body,
                    canSwitchSides = canSwitchSides
                )
            )
        } else {
            builder.setContentText(body)
        }

        postBreastfeedingGroupSummary(context)
        context.getSystemService(NotificationManager::class.java)
            .notify(BREASTFEEDING_ACTIVE_NOTIFICATION_ID, builder.build())
        Log.d(TAG, "showBreastfeedingActive posted (rich=$richEnabled)")
    }

    private fun NotificationCompat.Builder.applyBreastfeedingActiveRichContent(content: ActiveNotificationContent) {
        val isPaused = content.pausedAtEpochMs != null
        val timing = activeNotificationTiming(
            sessionStartEpochMs = content.sessionStartEpochMs,
            pausedDurationMs = content.pausedDurationMs,
            pausedAtEpochMs = content.pausedAtEpochMs
        )
        val progress = breastfeedingActiveProgress(timing.elapsedSeconds, content.maxTotalMinutes)

        setUsesChronometer(!isPaused)
            .setShowWhen(!isPaused)
            .setWhen(content.sessionStartEpochMs + content.pausedDurationMs)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomContentView(
                buildFeedingActiveCollapsedView(
                    context = content.context,
                    layoutRes = R.layout.notification_collapsed_feeding_active,
                    progress = progress.current,
                    maxProgress = progress.max,
                    showProgress = progress.isEnabled,
                    body = content.body,
                    timer = CollapsedTimerContent(
                        titleSuffix = content.context.getString(
                            if (isPaused) R.string.notif_status_feeding_paused else R.string.notif_status_feeding_active
                        ),
                        chronometerBaseElapsedMs = timing.chronometerBaseElapsedMs,
                        chronometerRunning = !isPaused
                    )
                )
            )
            .setCustomBigContentView(
                buildProgressBigView(
                    context = content.context,
                    layoutRes = R.layout.notification_breastfeeding_progress,
                    title = content.title,
                    body = content.body,
                    progress = progress.current,
                    maxProgress = progress.max,
                    progressText = progress.label,
                    chronometerBaseElapsedMs = timing.chronometerBaseElapsedMs,
                    chronometerRunning = !isPaused,
                    showProgress = progress.isEnabled
                )
            )
            .addAction(0, pauseResumeLabel(content.context, isPaused), pauseResumePendingIntent(content.context, content.sessionId, isPaused))

        if (!isPaused && content.canSwitchSides) {
            addAction(
                0,
                content.context.getString(R.string.notif_action_switch_sides),
                breastfeedingActionPi(content.context, content.sessionId, BreastfeedingActionReceiver.ACTION_SWITCH, RC_SWITCH_BF_ACTIVE)
            )
        }

        addAction(
            0,
            content.context.getString(R.string.notif_action_stop_session),
            breastfeedingActionPi(
                content.context,
                content.sessionId,
                BreastfeedingActionReceiver.ACTION_STOP,
                RC_STOP_BF_ACTIVE
            )
        )

        if (progress.isEnabled && !isPaused) {
            scheduleBreastfeedingActiveRefresh(content.context, content.sessionId)
        }
    }

    private fun breastfeedingActiveProgress(elapsedSeconds: Int, maxTotalMinutes: Int): ActiveProgress {
        val maxSeconds = maxTotalMinutes * SECONDS_PER_MINUTE
        val progressEnabled = maxSeconds > 0
        return ActiveProgress(
            current = if (progressEnabled) elapsedSeconds.coerceAtMost(maxSeconds) else 0,
            max = if (progressEnabled) maxSeconds else 1,
            label = if (progressEnabled) activeProgressText(maxTotalMinutes) else formatDurationCompact(elapsedSeconds),
            isEnabled = progressEnabled
        )
    }

    private fun pauseResumeLabel(context: Context, isPaused: Boolean): String =
        context.getString(if (isPaused) R.string.notif_action_resume else R.string.notif_action_pause)

    private fun pauseResumePendingIntent(context: Context, sessionId: Long, isPaused: Boolean): PendingIntent =
        breastfeedingActionPi(
            context = context,
            sessionId = sessionId,
            action = if (isPaused) BreastfeedingActionReceiver.ACTION_RESUME else BreastfeedingActionReceiver.ACTION_PAUSE,
            requestCode = if (isPaused) RC_RESUME_BF_ACTIVE else RC_PAUSE_BF_ACTIVE
        )

    fun showSleepActive(
        context: Context,
        sessionId: Long,
        sleepType: String,
        startTimeEpochMs: Long,
        richEnabled: Boolean
    ) {
        val typeLabel = context.getString(if (sleepType == "NIGHT_SLEEP") R.string.notif_sleep_type_night else R.string.notif_sleep_type_nap)
        val startFormatted = java.time.Instant.ofEpochMilli(startTimeEpochMs)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("h:mm a"))
        val title = context.getString(R.string.notif_title_sleep_active)
        val body = context.getString(R.string.notif_body_sleep_active, typeLabel, startFormatted)
        val tapPi = mainActivityPendingIntent(context)
        val accent = resolveAccent(context, Blue700, SecondaryBlueDark)
        val builder = NotificationCompat.Builder(context, SLEEP_CHANNEL_ID)
            .applyDesignSystem(accent, R.drawable.ic_notif_sleep)
            .setContentTitle(title)
            .setContentText(body)
            .setTicker(title)
            .setAutoCancel(false)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setGroup(SLEEP_GROUP_KEY)
            .setContentIntent(tapPi)

        if (richEnabled) {
            val elapsedMs = System.currentTimeMillis() - startTimeEpochMs
            val chronometerBase = SystemClock.elapsedRealtime() - elapsedMs
            builder
                .setUsesChronometer(true)
                .setWhen(startTimeEpochMs)
                .setCustomContentView(
                    buildCollapsedView(
                        context = context,
                        layoutRes = R.layout.notification_collapsed_sleep,
                        title = title,
                        body = body,
                        progress = 0,
                        maxProgress = 1,
                        showProgress = false,
                        titleIconRes = R.drawable.ic_sleep_section,
                    )
                )
                .setCustomBigContentView(
                    buildChronometerBigView(
                        context = context,
                        layoutRes = R.layout.notification_sleep_expanded,
                        title = title,
                        body = body,
                        chronometerBaseElapsedMs = chronometerBase,
                        chronometerRunning = true,
                        titleIconRes = R.drawable.ic_sleep_section,
                    )
                )
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .addAction(0, context.getString(R.string.notif_action_end_sleep), sleepActionPi(context, sessionId, SleepActionReceiver.ACTION_STOP, RC_STOP_SLEEP))
        } else {
            builder.setContentText(body)
        }

        context.getSystemService(NotificationManager::class.java)
            .notify(SLEEP_NOTIFICATION_ID, builder.build())
        Log.d(TAG, "showSleepActive posted (rich=$richEnabled)")
    }

    private fun postBreastfeedingGroupSummary(context: Context) {
        val accent = resolveAccent(context, Pink700, PrimaryPinkDark)
        val tapPi = mainActivityPendingIntent(context)
        val summary = NotificationCompat.Builder(context, BREASTFEEDING_CHANNEL_ID)
            .applyDesignSystem(accent, R.drawable.ic_notif_breastfeeding)
            .setContentTitle(context.getString(R.string.notif_title_breastfeeding_group))
            .setGroup(BREASTFEEDING_GROUP_KEY)
            .setGroupSummary(true)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(tapPi)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(BREASTFEEDING_GROUP_SUMMARY_NOTIFICATION_ID, summary)
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        context.getSystemService(NotificationManager::class.java).cancel(notificationId)
        Log.d(TAG, "Notification cancelled (ID: $notificationId)")
    }

    fun showNapReminder(context: Context) {
        val title = context.getString(R.string.notif_title_nap_reminder)
        val body = context.getString(R.string.notif_body_nap_reminder)
        val accent = resolveAccent(context, Blue700, SecondaryBlueDark)
        val tapPi = napReminderTapPendingIntent(context)
        val notification = NotificationCompat.Builder(context, SLEEP_CHANNEL_ID)
            .applyDesignSystem(accent, R.drawable.ic_notif_sleep)
            .setContentTitle(title)
            .setContentText(body)
            .setTicker(title)
            .setAutoCancel(true)
            .setOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setGroup(SLEEP_GROUP_KEY)
            .setContentIntent(tapPi)
            .build()
        context.getSystemService(NotificationManager::class.java)
            .notify(NAP_REMINDER_NOTIFICATION_ID, notification)
        Log.d(TAG, "showNapReminder posted")
    }

    private fun napReminderTapPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            RC_NAP_REMINDER_TAP,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(EXTRA_NAV_ROUTE, Routes.SLEEP_TRACKING)
            },
            PENDING_INTENT_IMMUTABLE_UPDATE,
        )

    private fun mainActivityPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, RC_MAIN_TAP,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PENDING_INTENT_IMMUTABLE_UPDATE
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
        PENDING_INTENT_IMMUTABLE_UPDATE
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
            PENDING_INTENT_IMMUTABLE_UPDATE
        )
        val canScheduleExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + ACTIVE_REFRESH_INTERVAL_MS,
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + ACTIVE_REFRESH_INTERVAL_MS,
                pendingIntent
            )
        }
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
        PENDING_INTENT_IMMUTABLE_UPDATE
    )

    const val PREDICTIVE_FEED_CHANNEL_ID = "predictive_feed_notifications"
    const val PREDICTIVE_FEED_NOTIFICATION_ID = 1006
    const val PREDICTIVE_SLEEP_CHANNEL_ID = "predictive_sleep_notifications"
    const val PREDICTIVE_SLEEP_NOTIFICATION_ID = 1008
    const val EXTRA_NAV_ROUTE = "com.babytracker.nav.route"
}
