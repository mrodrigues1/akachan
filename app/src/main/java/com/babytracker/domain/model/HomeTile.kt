package com.babytracker.domain.model

/**
 * Identifies every Home-screen card the user can reorder. Pure Kotlin (no framework imports) so it
 * can flow through the repository layer. Stored as a comma-joined list of [name]s in DataStore.
 */
enum class HomeTile {
    BREASTFEEDING,
    SLEEP,
    PUMPING,
    INVENTORY,
    BOTTLE_FEED,
    FEEDING_HISTORY,
    SLEEP_PREDICTION,
    GROWTH,
    TIP,
    PARTNER,
    ;

    companion object {
        /** Canonical order used when the user has never reordered. Mirrors the pre-feature layout. */
        val DEFAULT_ORDER: List<HomeTile> = listOf(
            BREASTFEEDING,
            SLEEP,
            PUMPING,
            INVENTORY,
            BOTTLE_FEED,
            FEEDING_HISTORY,
            SLEEP_PREDICTION,
            GROWTH,
            TIP,
            PARTNER,
        )

        /**
         * Reconcile a persisted order with the current tile set:
         * - keep stored tiles in their saved position,
         * - drop names that no longer map to a tile (removed in a later version),
         * - append tiles added since the order was saved, in their [DEFAULT_ORDER] sequence.
         */
        fun reconcile(stored: List<String>): List<HomeTile> {
            val known = stored.mapNotNull { name -> entries.firstOrNull { it.name == name } }.distinct()
            val seen = known.toSet()
            val appended = DEFAULT_ORDER.filter { it !in seen }
            return known + appended
        }

        fun serialize(order: List<HomeTile>): String = order.joinToString(",") { it.name }

        fun deserialize(raw: String?): List<HomeTile> =
            if (raw.isNullOrBlank()) {
                DEFAULT_ORDER
            } else {
                reconcile(raw.split(",").map(String::trim).filter { it.isNotEmpty() })
            }
    }
}
