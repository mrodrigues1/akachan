package com.babytracker.util

/**
 * Central registry of `PendingIntent` request codes used for notification/tile *tap* targets —
 * i.e. every `PendingIntent.getActivity(...)` that opens [com.babytracker.MainActivity].
 *
 * `Intent.filterEquals` ignores extras, comparing only action/data/type/component/categories.
 * Since every tap target here builds a plain `Intent(context, MainActivity::class.java)` with no
 * action/data/categories, two such PendingIntents with the *same request code* collapse into one
 * shared system record — whichever notification posts last silently rewrites the other's
 * `EXTRA_NAV_ROUTE` (see #745: `VaccineNotificationHelper` and `PartnerSleepNotificationHelper`
 * both used 3005). Every new tap target must claim a unique value here before wiring a
 * `PendingIntent`.
 */
object NotificationTapRequestCodes {
    const val MAIN = 0 // NotificationHelper.mainActivityPendingIntent
    const val PREDICTIVE_SLEEP_START = 2001 // PredictiveSleepNotificationHelper
    const val PREDICTIVE_FEED_START = 2010 // PredictiveFeedNotificationHelper
    const val NAP_REMINDER = 3002 // NotificationHelper.napReminderTapPendingIntent
    const val STASH_EXPIRATION = 3003 // StashNotificationHelper.stashExpirationTapPendingIntent
    const val PARTNER_STASH = 3004 // StashNotificationHelper.partnerStashTapPendingIntent
    const val VACCINE = 3005 // VaccineNotificationHelper.tapPendingIntent
    const val DOCTOR_VISIT = 3015 // DoctorVisitNotificationHelper.tapPendingIntent
    const val PARTNER_SLEEP = 3016 // PartnerSleepNotificationHelper.tapPendingIntent (was 3005 — collided with VACCINE)
    const val TILE_FEED = 80_001 // FeedTileService.activityRequestCode
    const val TILE_SLEEP = 80_002 // SleepTileService.activityRequestCode

    // Everything below is a PendingIntent.getBroadcast request code (action buttons + alarms), a
    // different PendingIntent "type" targeting a different component/action, so it cannot collide
    // with the getActivity/MainActivity codes above. Listed here only so the whole request-code
    // space stays visible in one file:
    //   NotificationHelper: RC_SWITCH_NOW=2001, RC_BF_DISMISS=2002, RC_STOP_SESSION=2003,
    //     RC_KEEP_GOING=2004, RC_STOP_SLEEP=2005, RC_STOP_BF_ACTIVE=2006,
    //     RC_REFRESH_BF_ACTIVE=2007, RC_PAUSE_BF_ACTIVE=2008, RC_RESUME_BF_ACTIVE=2009,
    //     RC_SWITCH_BF_ACTIVE=2010
    //   NapReminderManager.RC_NAP_REMINDER=3001
    //   StashExpirationNotificationManager.RC_STASH_EXPIRATION=4001
    //   VaccineReminderManager.RC_BASE=5000 (+ per-record vaccineId)
    //   DoctorVisitReminderManager.RC_BASE=6000 (+ per-record visitId)
}
