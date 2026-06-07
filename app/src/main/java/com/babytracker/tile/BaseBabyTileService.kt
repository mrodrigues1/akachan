package com.babytracker.tile

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

abstract class BaseBabyTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    protected abstract val baseLabel: String
    protected abstract val activityRequestCode: Int

    protected abstract suspend fun resolveState(entryPoint: TileEntryPoint): TileRenderState
    protected abstract suspend fun toggle(entryPoint: TileEntryPoint): TileToggleResult
    protected abstract fun intentForState(state: TileRenderState): Intent

    override fun onStartListening() {
        super.onStartListening()
        serviceScope.launch {
            renderCurrentState()
        }
    }

    override fun onClick() {
        super.onClick()
        serviceScope.launch {
            val entryPoint = tileEntryPoint()
            val state = resolveOrUnavailable(entryPoint)
            when (
                shouldOpenAppOnTap(
                    isSecure = isSecureDevice(),
                    isLocked = isLockedDevice(),
                    state = state.availability,
                )
            ) {
                TileTapAction.OPEN_APP -> startActivityAndCollapseCompat(intentForState(state))
                TileTapAction.TOGGLE_SILENTLY -> {
                    toggle(entryPoint)
                    renderCurrentState(entryPoint)
                }
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun renderCurrentState(entryPoint: TileEntryPoint = tileEntryPoint()) {
        val state = resolveOrUnavailable(entryPoint)
        qsTile?.apply {
            this.state = state.availability.toQsTileState()
            label = state.label
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = tileSubtitleForSdk(state)
            }
            updateTile()
        }
    }

    private suspend fun resolveOrUnavailable(entryPoint: TileEntryPoint): TileRenderState =
        runCatching {
            resolveState(entryPoint)
        }.getOrElse { error ->
            if (error is CancellationException) throw error
            Log.w(TAG, "Tile render failed for $baseLabel", error)
            TileRenderState(
                availability = TileAvailability.UNAVAILABLE,
                label = baseLabel,
                subtitle = null,
            )
        }

    private fun tileEntryPoint(): TileEntryPoint =
        EntryPointAccessors.fromApplication(
            applicationContext,
            TileEntryPoint::class.java,
        )

    private fun isSecureDevice(): Boolean =
        getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    private fun isLockedDevice(): Boolean = isLocked

    private fun startActivityAndCollapseCompat(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this,
                activityRequestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @SuppressLint("StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    private companion object {
        private const val TAG = "BabyTileService"
    }
}
