package com.babytracker.domain.sleep.prior

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalTime

class SleepAgePriorsTest {

    @Nested
    inner class DefaultWakeWindowsTests {
        @Test
        fun `newborn under 6 weeks has 5 wake windows of 45 minutes`() {
            val windows = SleepAgePriors.getDefaultWakeWindows(3)
            assertEquals(5, windows.size)
            assertTrue(windows.all { it == Duration.ofMinutes(45) })
        }

        @Test
        fun `6-8 weeks has 4 graduated wake windows`() {
            val windows = SleepAgePriors.getDefaultWakeWindows(7)
            assertEquals(4, windows.size)
            assertEquals(Duration.ofMinutes(60), windows[0])
            assertEquals(Duration.ofMinutes(75), windows[3])
        }

        @Test
        fun `2-3 months has 4 graduated wake windows`() {
            val windows = SleepAgePriors.getDefaultWakeWindows(10)
            assertEquals(4, windows.size)
            assertEquals(Duration.ofMinutes(75), windows[0])
            assertEquals(Duration.ofMinutes(90), windows[3])
        }

        @Test
        fun `3-4 months has 3 graduated wake windows`() {
            val windows = SleepAgePriors.getDefaultWakeWindows(14)
            assertEquals(3, windows.size)
            assertEquals(Duration.ofMinutes(90), windows[0])
            assertEquals(Duration.ofMinutes(120), windows[2])
        }

        @Test
        fun `4-6 months has 3 graduated wake windows`() {
            val windows = SleepAgePriors.getDefaultWakeWindows(20)
            assertEquals(3, windows.size)
            assertEquals(Duration.ofMinutes(105), windows[0])
            assertEquals(Duration.ofMinutes(150), windows[2])
        }

        @Test
        fun `6-9 months has 3 graduated wake windows`() {
            val windows = SleepAgePriors.getDefaultWakeWindows(30)
            assertEquals(3, windows.size)
            assertEquals(Duration.ofMinutes(150), windows[0])
            assertEquals(Duration.ofMinutes(210), windows[2])
        }

        @Test
        fun `9-12 months has 3 graduated wake windows`() {
            val windows = SleepAgePriors.getDefaultWakeWindows(40)
            assertEquals(3, windows.size)
            assertEquals(Duration.ofMinutes(180), windows[0])
            assertEquals(Duration.ofMinutes(240), windows[2])
        }

        @Test
        fun `wake windows are graduated - first is shortest, last is longest`() {
            val ageBrackets = listOf(3, 7, 10, 14, 20, 30, 40)
            for (age in ageBrackets) {
                val windows = SleepAgePriors.getDefaultWakeWindows(age)
                assertTrue(
                    windows.first() <= windows.last(),
                    "Age $age weeks: first window should be <= last window"
                )
            }
        }
    }

    @Nested
    inner class WakeWindowBoundsTests {
        @Test
        fun `newborn bounds are 30-60 minutes`() {
            val (min, max) = SleepAgePriors.getWakeWindowBounds(3)
            assertEquals(Duration.ofMinutes(30), min)
            assertEquals(Duration.ofMinutes(60), max)
        }

        @Test
        fun `6-8 weeks bounds are 45-90 minutes`() {
            val (min, max) = SleepAgePriors.getWakeWindowBounds(7)
            assertEquals(Duration.ofMinutes(45), min)
            assertEquals(Duration.ofMinutes(90), max)
        }

        @Test
        fun `2-3 months bounds are 60-120 minutes`() {
            val (min, max) = SleepAgePriors.getWakeWindowBounds(10)
            assertEquals(Duration.ofMinutes(60), min)
            assertEquals(Duration.ofMinutes(120), max)
        }

        @Test
        fun `3-4 months bounds are 75-150 minutes`() {
            val (min, max) = SleepAgePriors.getWakeWindowBounds(14)
            assertEquals(Duration.ofMinutes(75), min)
            assertEquals(Duration.ofMinutes(150), max)
        }

        @Test
        fun `4-6 months bounds are 90-180 minutes`() {
            val (min, max) = SleepAgePriors.getWakeWindowBounds(20)
            assertEquals(Duration.ofMinutes(90), min)
            assertEquals(Duration.ofMinutes(180), max)
        }

        @Test
        fun `6-9 months bounds are 120-210 minutes`() {
            val (min, max) = SleepAgePriors.getWakeWindowBounds(30)
            assertEquals(Duration.ofMinutes(120), min)
            assertEquals(Duration.ofMinutes(210), max)
        }

        @Test
        fun `9-12 months bounds are 150-240 minutes`() {
            val (min, max) = SleepAgePriors.getWakeWindowBounds(40)
            assertEquals(Duration.ofMinutes(150), min)
            assertEquals(Duration.ofMinutes(240), max)
        }
    }

    @Nested
    inner class BedtimeWindowTests {
        @Test
        fun `newborn bedtime window is 9-11 PM`() {
            val window = SleepAgePriors.getBedtimeWindow(3)
            assertEquals(LocalTime.of(21, 0), window.start)
            assertEquals(LocalTime.of(23, 0), window.endInclusive)
        }

        @Test
        fun `6-12 week bedtime window is 8-10 PM`() {
            val window = SleepAgePriors.getBedtimeWindow(8)
            assertEquals(LocalTime.of(20, 0), window.start)
            assertEquals(LocalTime.of(22, 0), window.endInclusive)
        }

        @Test
        fun `3-4 month bedtime window is 7_30-9 PM`() {
            val window = SleepAgePriors.getBedtimeWindow(14)
            assertEquals(LocalTime.of(19, 30), window.start)
            assertEquals(LocalTime.of(21, 0), window.endInclusive)
        }

        @Test
        fun `5-6 month bedtime window is 7-8 PM`() {
            val window = SleepAgePriors.getBedtimeWindow(20)
            assertEquals(LocalTime.of(19, 0), window.start)
            assertEquals(LocalTime.of(20, 0), window.endInclusive)
        }

        @Test
        fun `7-12 month bedtime window is 6_30-8 PM`() {
            val window = SleepAgePriors.getBedtimeWindow(30)
            assertEquals(LocalTime.of(18, 30), window.start)
            assertEquals(LocalTime.of(20, 0), window.endInclusive)
        }
    }

    @Nested
    inner class TotalSleepRecommendationTests {
        @Test
        fun `0-4 months recommends 14-17 hours`() {
            val rec = SleepAgePriors.getTotalSleepRecommendation(10)
            assertEquals(Duration.ofHours(14), rec.start)
            assertEquals(Duration.ofHours(17), rec.endInclusive)
        }

        @Test
        fun `4-12 months recommends 12-16 hours`() {
            val rec = SleepAgePriors.getTotalSleepRecommendation(20)
            assertEquals(Duration.ofHours(12), rec.start)
            assertEquals(Duration.ofHours(16), rec.endInclusive)
        }
    }

    @Nested
    inner class NapTransitionThresholdTests {
        @Test
        fun `newborn under 6 weeks transition threshold is 5`() {
            assertEquals(5, SleepAgePriors.getNapTransitionThreshold(3))
        }

        @Test
        fun `6-12 weeks transition threshold is 4`() {
            assertEquals(4, SleepAgePriors.getNapTransitionThreshold(8))
        }

        @Test
        fun `12-24 weeks transition threshold is 3`() {
            assertEquals(3, SleepAgePriors.getNapTransitionThreshold(16))
        }

        @Test
        fun `24-36 weeks transition threshold is 2`() {
            assertEquals(2, SleepAgePriors.getNapTransitionThreshold(28))
        }

        @Test
        fun `over 36 weeks transition threshold is 2`() {
            assertEquals(2, SleepAgePriors.getNapTransitionThreshold(40))
        }
    }

    @Nested
    inner class ScheduledNapCountTests {
        @Test
        fun `scheduled nap count is wake-window count minus 1`() {
            listOf(3, 7, 10, 14, 20, 30, 40).forEach { age ->
                assertEquals(
                    SleepAgePriors.getDefaultWakeWindows(age).size - 1,
                    SleepAgePriors.getScheduledNapCount(age),
                    "Age $age weeks: scheduledNapCount should be wakeWindows.size - 1"
                )
            }
        }

        @Test
        fun `newborn under 6 weeks schedules 4 naps`() {
            assertEquals(4, SleepAgePriors.getScheduledNapCount(3))
        }

        @Test
        fun `6-12 weeks schedules 3 naps`() {
            assertEquals(3, SleepAgePriors.getScheduledNapCount(8))
        }

        @Test
        fun `3-12 months schedules 2 naps`() {
            assertEquals(2, SleepAgePriors.getScheduledNapCount(20))
        }
    }

    @Nested
    inner class NapCountParityTests {
        // For ages < 24 weeks the transition threshold is one higher than the scheduled nap
        // count — young infants have no distinct bedtime in the sleep-science model, so the
        // biological count includes what the scheduler treats as "last wake window → bedtime".
        // For ages >= 24 weeks they converge. These tests document the intentional divergence.

        @Test
        fun `for ages under 24 weeks transition threshold exceeds scheduled nap count by 1`() {
            listOf(3, 7, 10, 14, 20).forEach { age ->
                assertEquals(
                    SleepAgePriors.getScheduledNapCount(age) + 1,
                    SleepAgePriors.getNapTransitionThreshold(age),
                    "Age $age weeks: transitionThreshold should be scheduledNapCount + 1"
                )
            }
        }

        @Test
        fun `for ages 24 weeks and over transition threshold matches scheduled nap count`() {
            listOf(24, 30, 40).forEach { age ->
                assertEquals(
                    SleepAgePriors.getScheduledNapCount(age),
                    SleepAgePriors.getNapTransitionThreshold(age),
                    "Age $age weeks: transitionThreshold should equal scheduledNapCount"
                )
            }
        }
    }

    @Nested
    inner class RegressionTests {
        @Test
        fun `4 month regression detected at 16 weeks`() {
            val info = SleepAgePriors.detectRegression(16)
            assertNotNull(info)
            assertEquals("4-Month Sleep Regression", info!!.name)
        }

        @Test
        fun `4 month regression detected at 20 weeks`() {
            val info = SleepAgePriors.detectRegression(20)
            assertNotNull(info)
            assertEquals("4-Month Sleep Regression", info!!.name)
        }

        @Test
        fun `8-10 month regression detected at 36 weeks`() {
            val info = SleepAgePriors.detectRegression(36)
            assertNotNull(info)
            assertEquals("8-10 Month Sleep Regression", info!!.name)
        }

        @Test
        fun `12 month regression detected at 50 weeks`() {
            val info = SleepAgePriors.detectRegression(50)
            assertNotNull(info)
            assertEquals("12-Month Sleep Regression", info!!.name)
        }

        @Test
        fun `no regression at 10 weeks`() {
            assertNull(SleepAgePriors.detectRegression(10))
        }

        @Test
        fun `no regression at 26 weeks`() {
            assertNull(SleepAgePriors.detectRegression(26))
        }

        @Test
        fun `no regression at 46 weeks`() {
            assertNull(SleepAgePriors.detectRegression(46))
        }
    }
}
