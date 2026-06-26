package com.babytracker.manager

import android.content.Context
import com.babytracker.sharing.usecase.PartnerSleepNotification
import com.babytracker.util.PartnerSleepNotificationHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PartnerSleepNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : PartnerSleepNotifier {

    override suspend fun notifySleepChanges(notifications: List<PartnerSleepNotification>) {
        // Coalesce: the most recent change is the one worth surfacing (e.g. start then stop in one
        // batch -> "ended"). The id is fixed, so a later notification replaces the previous one.
        val latest = notifications.lastOrNull() ?: return
        PartnerSleepNotificationHelper.showPartnerSleepChange(context, latest)
    }
}
