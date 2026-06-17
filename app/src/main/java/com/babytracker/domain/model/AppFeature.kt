package com.babytracker.domain.model

/**
 * Every togglable tracker. Pure Kotlin (no framework imports) so it flows through the repository
 * layer. Stored as a comma-joined list of [name]s in DataStore, like [HomeTile].
 */
enum class AppFeature {
    BREASTFEEDING,
    BOTTLE_FEED,
    PUMPING,
    INVENTORY,
    SLEEP,
    DIAPERS,
    GROWTH,
    MILESTONES,
    ;

    companion object {
        /** Default when the user has never chosen: everything on (migration-free for existing users). */
        val ALL: Set<AppFeature> = entries.toSet()

        fun serialize(features: Set<AppFeature>): String = features.joinToString(",") { it.name }

        /**
         * Parse stored names. Unknown names are dropped. A null/blank input, or an input that
         * yields no known feature, falls back to [ALL] so the set is never empty.
         */
        fun deserialize(raw: String?): Set<AppFeature> {
            if (raw.isNullOrBlank()) return ALL
            val parsed = raw.split(",")
                .map(String::trim)
                .mapNotNull { name -> entries.firstOrNull { it.name == name } }
                .toSet()
            return parsed.ifEmpty { ALL }
        }
    }
}
