package com.babytracker.widget

import android.content.ComponentName
import android.content.Intent
import androidx.glance.action.Action
import androidx.glance.appwidget.action.actionStartActivity
import com.babytracker.MainActivity
import com.babytracker.navigation.Routes
import com.babytracker.util.NotificationHelper

internal fun openHomeAction(): Action = actionStartActivity(
    intent = mainActivityIntent(route = Routes.HOME),
)

internal fun openBreastfeedingAction(): Action = actionStartActivity(
    intent = mainActivityIntent(route = Routes.BREASTFEEDING),
)

internal fun openSleepAction(): Action = actionStartActivity(
    intent = mainActivityIntent(route = Routes.SLEEP_TRACKING),
)

private fun mainActivityIntent(route: String): Intent {
    val intent = Intent()
    intent.component = ComponentName("com.babytracker", MainActivity::class.java.name)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    intent.putExtra(NotificationHelper.EXTRA_NAV_ROUTE, route)
    return intent
}
