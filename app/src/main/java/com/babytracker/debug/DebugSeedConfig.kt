package com.babytracker.debug

/** Debug-only knobs for [DebugDataSeeder]. Shared so the offline partner-data seam can match too. */
object DebugSeedConfig {
    // true boots the debug build into the partner dashboard (local flip, no Firebase).
    const val ENTER_PARTNER_MODE = false

    // Placeholder share code for the local partner flip. FetchPartnerDataUseCase recognises it and
    // serves a snapshot of the locally-seeded data instead of hitting Firebase.
    const val PARTNER_SHARE_CODE = "SEED0001"
}
