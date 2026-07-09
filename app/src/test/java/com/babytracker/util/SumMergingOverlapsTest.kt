package com.babytracker.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class SumMergingOverlapsTest {

    @Test
    fun `empty list sums to zero`() {
        val result = sumMergingOverlaps(emptyList())

        assertEquals(Duration.ZERO, result)
    }

    @Test
    fun `single interval sums to its own span`() {
        val start = Instant.parse("2026-04-09T09:00:00Z")
        val end = Instant.parse("2026-04-09T10:00:00Z")

        val result = sumMergingOverlaps(listOf(start to end))

        assertEquals(Duration.ofHours(1), result)
    }

    @Test
    fun `non-overlapping intervals sum naively`() {
        val a = Instant.parse("2026-04-09T09:00:00Z") to Instant.parse("2026-04-09T10:00:00Z")
        val b = Instant.parse("2026-04-09T13:00:00Z") to Instant.parse("2026-04-09T14:30:00Z")

        val result = sumMergingOverlaps(listOf(a, b))

        assertEquals(Duration.ofMinutes(150), result)
    }

    @Test
    fun `fully contained interval does not double count`() {
        val outer = Instant.parse("2026-04-09T00:00:00Z") to Instant.parse("2026-04-09T06:00:00Z")
        val inner = Instant.parse("2026-04-09T02:00:00Z") to Instant.parse("2026-04-09T03:00:00Z")

        val result = sumMergingOverlaps(listOf(outer, inner))

        assertEquals(Duration.ofHours(6), result)
    }

    @Test
    fun `partially overlapping intervals merge into one span`() {
        val a = Instant.parse("2026-04-09T09:00:00Z") to Instant.parse("2026-04-09T10:00:00Z")
        val b = Instant.parse("2026-04-09T09:30:00Z") to Instant.parse("2026-04-09T11:00:00Z")

        val result = sumMergingOverlaps(listOf(a, b))

        assertEquals(Duration.ofHours(2), result)
    }

    @Test
    fun `touching intervals (one starts exactly when the other ends) merge without double counting`() {
        val a = Instant.parse("2026-04-09T09:00:00Z") to Instant.parse("2026-04-09T10:00:00Z")
        val b = Instant.parse("2026-04-09T10:00:00Z") to Instant.parse("2026-04-09T11:00:00Z")

        val result = sumMergingOverlaps(listOf(a, b))

        assertEquals(Duration.ofHours(2), result)
    }

    @Test
    fun `adjacent intervals with a gap sum naively`() {
        val a = Instant.parse("2026-04-09T09:00:00Z") to Instant.parse("2026-04-09T10:00:00Z")
        val b = Instant.parse("2026-04-09T10:05:00Z") to Instant.parse("2026-04-09T11:00:00Z")

        val result = sumMergingOverlaps(listOf(a, b))

        assertEquals(Duration.ofMinutes(115), result)
    }

    @Test
    fun `unsorted input is merged the same as sorted input`() {
        val a = Instant.parse("2026-04-09T13:00:00Z") to Instant.parse("2026-04-09T14:30:00Z")
        val b = Instant.parse("2026-04-09T09:00:00Z") to Instant.parse("2026-04-09T10:00:00Z")
        val c = Instant.parse("2026-04-09T09:30:00Z") to Instant.parse("2026-04-09T09:45:00Z")

        val result = sumMergingOverlaps(listOf(a, b, c))

        assertEquals(Duration.ofMinutes(150), result)
    }

    @Test
    fun `three-way overlap across a whole cluster merges into a single span`() {
        val a = Instant.parse("2026-04-09T09:00:00Z") to Instant.parse("2026-04-09T11:00:00Z")
        val b = Instant.parse("2026-04-09T10:00:00Z") to Instant.parse("2026-04-09T12:00:00Z")
        val c = Instant.parse("2026-04-09T11:30:00Z") to Instant.parse("2026-04-09T13:00:00Z")

        val result = sumMergingOverlaps(listOf(a, b, c))

        assertEquals(Duration.ofHours(4), result)
    }
}
