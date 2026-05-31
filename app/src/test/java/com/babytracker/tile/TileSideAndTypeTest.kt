package com.babytracker.tile

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class TileSideAndTypeTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2024, 1, 15)

    private fun session(
        startingSide: BreastSide,
        switchTime: Instant? = null,
    ) = BreastfeedingSession(
        startTime = Instant.EPOCH,
        startingSide = startingSide,
        switchTime = switchTime,
    )

    private fun instantAt(hour: Int, minute: Int = 0): Instant =
        today.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant()

    // alternateSide tests

    @Test
    fun noLastSessionDefaultsToLeft() {
        assertEquals(BreastSide.LEFT, alternateSide(null))
    }

    @Test
    fun unswitchedLeftSessionAlternatesToRight() {
        assertEquals(BreastSide.RIGHT, alternateSide(session(BreastSide.LEFT)))
    }

    @Test
    fun unswitchedRightSessionAlternatesToLeft() {
        assertEquals(BreastSide.LEFT, alternateSide(session(BreastSide.RIGHT)))
    }

    @Test
    fun switchedLeftStartSessionFinalSideIsRightNextIsLeft() {
        val switched = session(BreastSide.LEFT, switchTime = Instant.EPOCH.plusSeconds(300))
        assertEquals(BreastSide.RIGHT, switched.finalSide())
        assertEquals(BreastSide.LEFT, alternateSide(switched))
    }

    @Test
    fun switchedRightStartSessionFinalSideIsLeftNextIsRight() {
        val switched = session(BreastSide.RIGHT, switchTime = Instant.EPOCH.plusSeconds(300))
        assertEquals(BreastSide.LEFT, switched.finalSide())
        assertEquals(BreastSide.RIGHT, alternateSide(switched))
    }

    // sleepTypeFor boundary tests

    @Test
    fun time1859IsNap() {
        assertEquals(SleepType.NAP, sleepTypeFor(instantAt(18, 59), zone))
    }

    @Test
    fun time1900IsNightSleep() {
        assertEquals(SleepType.NIGHT_SLEEP, sleepTypeFor(instantAt(19, 0), zone))
    }

    @Test
    fun time0200IsNightSleep() {
        assertEquals(SleepType.NIGHT_SLEEP, sleepTypeFor(instantAt(2, 0), zone))
    }

    @Test
    fun time0600IsNap() {
        assertEquals(SleepType.NAP, sleepTypeFor(instantAt(6, 0), zone))
    }

    @Test
    fun middayIsNap() {
        assertEquals(SleepType.NAP, sleepTypeFor(instantAt(12, 0), zone))
    }
}
