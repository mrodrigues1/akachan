package com.babytracker.tile

import android.content.Intent
import com.babytracker.util.NotificationTapRequestCodes

class SleepTileService : BaseBabyTileService() {

    override val baseLabel: String = "Sleep"
    override val activityRequestCode: Int = NotificationTapRequestCodes.TILE_SLEEP

    override suspend fun resolveState(entryPoint: TileEntryPoint): TileRenderState =
        TileStateResolver(
            settingsRepository = entryPoint.settingsRepository(),
            breastfeedingRepository = entryPoint.breastfeedingRepository(),
            sleepRepository = entryPoint.sleepRepository(),
            clock = entryPoint.clock(),
        ).resolveSleep()

    override suspend fun toggle(entryPoint: TileEntryPoint): TileToggleResult =
        entryPoint.tileToggleHandler().toggleSleep()

    override fun intentForState(state: TileRenderState): Intent =
        sleepIntentForState(this, state)
}
