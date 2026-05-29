package com.babytracker.widget

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.babytracker.domain.model.BreastSide
import com.babytracker.widget.data.FeedState
import com.babytracker.widget.data.SleepState
import com.babytracker.widget.data.WidgetData
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PartnerWidgetCacheImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : PartnerWidgetCache {

    override suspend fun read(shareCode: String): WidgetData? {
        val prefs = dataStore.data.first()
        val feedStateName = prefs[FEED_STATE] ?: return null
        if (prefs[SHARE_CODE] != shareCode) return null

        return WidgetData(
            babyName = prefs[BABY_NAME] ?: "Baby",
            lastFeedSide = prefs[FEED_SIDE]?.let(BreastSide::valueOf),
            lastFeedStart = prefs[FEED_START_MS]?.let(Instant::ofEpochMilli),
            feedState = FeedState.valueOf(feedStateName),
            sleepState = SleepState.valueOf(prefs[SLEEP_STATE] ?: SleepState.NONE.name),
            sleepSince = prefs[SLEEP_SINCE_MS]?.let(Instant::ofEpochMilli),
        )
    }

    override suspend fun save(shareCode: String, data: WidgetData) {
        dataStore.edit { prefs ->
            prefs[SHARE_CODE] = shareCode
            prefs[BABY_NAME] = data.babyName
            data.lastFeedSide?.let { prefs[FEED_SIDE] = it.name } ?: prefs.remove(FEED_SIDE)
            data.lastFeedStart?.let { prefs[FEED_START_MS] = it.toEpochMilli() } ?: prefs.remove(FEED_START_MS)
            prefs[FEED_STATE] = data.feedState.name
            prefs[SLEEP_STATE] = data.sleepState.name
            data.sleepSince?.let { prefs[SLEEP_SINCE_MS] = it.toEpochMilli() } ?: prefs.remove(SLEEP_SINCE_MS)
        }
    }

    override suspend fun clear(shareCode: String) {
        dataStore.edit { prefs ->
            // Code-scoped clear: a concurrent reconnect that already cached a different primary's
            // data keeps it. And because this is the shared "baby_tracker_prefs" store, we remove
            // only the partner_widget_* keys — NEVER prefs.clear(), which would wipe app settings.
            if (prefs[SHARE_CODE] != shareCode) return@edit
            prefs.remove(SHARE_CODE)
            prefs.remove(BABY_NAME)
            prefs.remove(FEED_SIDE)
            prefs.remove(FEED_START_MS)
            prefs.remove(FEED_STATE)
            prefs.remove(SLEEP_STATE)
            prefs.remove(SLEEP_SINCE_MS)
        }
    }

    private companion object {
        val BABY_NAME = stringPreferencesKey("partner_widget_baby_name")
        val FEED_SIDE = stringPreferencesKey("partner_widget_feed_side")
        val FEED_START_MS = longPreferencesKey("partner_widget_feed_start_ms")
        val FEED_STATE = stringPreferencesKey("partner_widget_feed_state")
        val SLEEP_STATE = stringPreferencesKey("partner_widget_sleep_state")
        val SLEEP_SINCE_MS = longPreferencesKey("partner_widget_sleep_since_ms")
        val SHARE_CODE = stringPreferencesKey("partner_widget_share_code")
    }
}
