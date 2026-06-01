package com.babytracker.tile

import android.content.Intent

class FeedTileService : BaseBabyTileService() {

    override val baseLabel: String = "Feed"
    override val activityRequestCode: Int = 80_001

    override suspend fun resolveState(entryPoint: TileEntryPoint): TileRenderState =
        TileStateResolver(
            settingsRepository = entryPoint.settingsRepository(),
            breastfeedingRepository = entryPoint.breastfeedingRepository(),
            sleepRepository = entryPoint.sleepRepository(),
            clock = entryPoint.clock(),
        ).resolveFeed()

    override suspend fun toggle(entryPoint: TileEntryPoint): TileToggleResult =
        entryPoint.tileToggleHandler().toggleFeed()

    override fun intentForState(state: TileRenderState): Intent =
        feedIntentForState(this, state)
}
