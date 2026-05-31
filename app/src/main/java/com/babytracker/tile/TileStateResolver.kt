package com.babytracker.tile

import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.sharing.domain.model.AppMode
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.Duration
import java.time.Instant

class TileStateResolver(
    private val settingsRepository: SettingsRepository,
    private val breastfeedingRepository: BreastfeedingRepository,
    private val sleepRepository: SleepRepository,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    suspend fun resolveFeed(): TileRenderState = runCatching {
        if (!isAvailable()) return@runCatching unavailable("Feed")
        val active = breastfeedingRepository.getActiveSession().first()
        if (active != null) {
            TileRenderState(
                availability = TileAvailability.ACTIVE,
                label = "Stop Feed",
                subtitle = elapsedSubtitle(active.startTime),
            )
        } else {
            TileRenderState(
                availability = TileAvailability.INACTIVE,
                label = "Start Feed",
                subtitle = null,
            )
        }
    }.getOrElse { unavailable("Feed") }

    suspend fun resolveSleep(): TileRenderState = runCatching {
        if (!isAvailable()) return@runCatching unavailable("Sleep")
        val latest = sleepRepository.getLatestRecord()
        if (latest != null && latest.isInProgress) {
            TileRenderState(
                availability = TileAvailability.ACTIVE,
                label = "Stop Sleep",
                subtitle = elapsedSubtitle(latest.startTime),
            )
        } else {
            TileRenderState(
                availability = TileAvailability.INACTIVE,
                label = "Start Sleep",
                subtitle = null,
            )
        }
    }.getOrElse { unavailable("Sleep") }

    private suspend fun isAvailable(): Boolean {
        val onboarded = settingsRepository.isOnboardingComplete().first()
        val mode = settingsRepository.getAppMode().first()
        return onboarded && mode != AppMode.PARTNER
    }

    private fun elapsedSubtitle(startTime: Instant): String {
        val elapsed = Duration.between(startTime, Instant.now(clock))
            .coerceAtLeast(Duration.ZERO)
        val hours = elapsed.toHours()
        val minutes = elapsed.toMinutes() % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    private fun unavailable(baseLabel: String) = TileRenderState(
        availability = TileAvailability.UNAVAILABLE,
        label = baseLabel,
        subtitle = "Open app",
    )
}
