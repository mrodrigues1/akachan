package com.babytracker.widget

import com.babytracker.widget.data.WidgetData

/**
 * Caches the projected [WidgetData] for a partner-mode widget, tagged with the share code it was
 * fetched under. [read] returns data only when the requested code matches the cached one, and
 * [clear] removes the cache only when the supplied code matches — so a partner who reconnects to a
 * different primary never sees, nor has wiped, the other primary's data.
 */
interface PartnerWidgetCache {
    suspend fun read(shareCode: String): WidgetData?
    suspend fun save(shareCode: String, data: WidgetData)
    suspend fun clear(shareCode: String)
}
