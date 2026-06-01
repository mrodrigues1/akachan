package com.babytracker.tile

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.babytracker.MainActivity
import com.babytracker.navigation.Routes
import com.babytracker.util.NotificationHelper

fun tileMainActivityIntent(context: Context, route: String): Intent =
    Intent().apply {
        component = ComponentName(context.packageName, MainActivity::class.java.name)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        putExtra(NotificationHelper.EXTRA_NAV_ROUTE, route)
    }

fun feedTileIntent(context: Context): Intent =
    tileMainActivityIntent(context, Routes.BREASTFEEDING)

fun sleepTileIntent(context: Context): Intent =
    tileMainActivityIntent(context, Routes.SLEEP_TRACKING)

fun unavailableTileIntent(context: Context): Intent =
    Intent().apply {
        component = ComponentName(context.packageName, MainActivity::class.java.name)
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

internal fun feedIntentForState(context: Context, state: TileRenderState): Intent =
    if (state.availability == TileAvailability.UNAVAILABLE) {
        unavailableTileIntent(context)
    } else {
        feedTileIntent(context)
    }

internal fun sleepIntentForState(context: Context, state: TileRenderState): Intent =
    if (state.availability == TileAvailability.UNAVAILABLE) {
        unavailableTileIntent(context)
    } else {
        sleepTileIntent(context)
    }
