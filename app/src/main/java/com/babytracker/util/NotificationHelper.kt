package com.babytracker.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.util.Log
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
    private const val TAG = "NotificationHelper"

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
        val body = "$sideLabel breast: $elapsedMinutes min · Switch to the $otherSide"
        val tapPi = mainActivityPendingIntent(context)
        val accent = resolveAccent(context, Pink700, PrimaryPinkDark)
        val builder = NotificationCompat.Builder(context, BREASTFEEDING_CHANNEL_ID)
            .applyDesignSystem(accent, R.drawable.ic_notif_breastfeeding)
            .setContentTitle("🍼 Time to switch sides")
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(tapPi)

        if (richEnabled) {
            builder
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setProgress(maxPerBreastMinutes, elapsedMinutes, false)
                .setSubText("$elapsedMinutes / $maxPerBreastMinutes min")
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
        val body = "Session has reached $maxTotalMinutes minutes. Consider wrapping up."
        val tapPi = mainActivityPendingIntent(context)
        val accent = resolveAccent(context, WarningAmber, WarningAmberDark)
        val builder = NotificationCompat.Builder(context, BREASTFEEDING_CHANNEL_ID)
            .applyDesignSystem(accent, R.drawable.ic_notif_limit)
            .setContentTitle("⏱ Feeding limit reached")
            .setAutoCancel(true)
            .setOngoing(false)
            .setContentIntent(tapPi)

        if (richEnabled) {
            builder
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setProgress(maxTotalMinutes, maxTotalMinutes, false)
                .setSubText("$maxTotalMinutes / $maxTotalMinutes min")
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
        richEnabled: Boolean
    ) {
        val sideLabel = if (currentSide == "LEFT") "Left" else "Right"
        val body = "$sideLabel breast · Session in progress"
        val tapPi = mainActivityPendingIntent(context)
        val accent = resolveAccent(context, Pink700, PrimaryPinkDark)
        val builder = NotificationCompat.Builder(context, BREASTFEEDING_CHANNEL_ID)
            .applyDesignSystem(accent, R.drawable.ic_notif_breastfeeding)
            .setContentTitle("🍼 Feeding session active")
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(tapPi)

        if (richEnabled) {
            builder
                .setUsesChronometer(true)
                .setWhen(sessionStartEpochMs + pausedDurationMs)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .addAction(0, "Stop Session", breastfeedingActionPi(context, sessionId, BreastfeedingActionReceiver.ACTION_STOP, RC_STOP_BF_ACTIVE))
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
        val body = "$typeLabel · started at $startFormatted"
        val tapPi = mainActivityPendingIntent(context)
        val accent = resolveAccent(context, Blue700, SecondaryBlueDark)
        val builder = NotificationCompat.Builder(context, SLEEP_CHANNEL_ID)
            .applyDesignSystem(accent, R.drawable.ic_notif_sleep)
            .setContentTitle("🌙 Sleep session active")
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
