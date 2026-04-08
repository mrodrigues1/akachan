package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.ScheduleMode
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SleepRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit

class GenerateSleepScheduleUseCaseTest {

    private lateinit var sleepRepository: SleepRepository
    private lateinit var breastfeedingRepository: BreastfeedingRepository
    private lateinit var useCase: GenerateSleepScheduleUseCase

    @BeforeEach
    fun setUp() {
        sleepRepository = mockk()
        breastfeedingRepository = mockk()
        useCase = GenerateSleepScheduleUseCase(sleepRepository, breastfeedingRepository)
    }

    private fun babyOfAge(weeks: Int): Baby = Baby(
        name = "Test Baby",
        birthDate = LocalDate.now().minusWeeks(weeks.toLong())
    )

    private fun setupEmptyData() {
        coEvery { sleepRepository.getCompletedRecordsSince(any()) } returns emptyList()
        coEvery { breastfeedingRepository.getLastSession() } returns null
    }

    // --- Schedule Mode ---

    @Nested
    inner class ScheduleModeTests {
        @Test
        fun `baby under 16 weeks gets demand-driven mode`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(10))
            assertEquals(ScheduleMode.DEMAND_DRIVEN, schedule.mode)
        }

        @Test
        fun `baby at 16 weeks gets clock-aligned mode`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(16))
            assertEquals(ScheduleMode.CLOCK_ALIGNED, schedule.mode)
        }

        @Test
        fun `baby at 30 weeks gets clock-aligned mode`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(30))
            assertEquals(ScheduleMode.CLOCK_ALIGNED, schedule.mode)
        }
    }

    // --- Wake Windows ---

    @Nested
    inner class WakeWindowTests {
        @Test
        fun `newborn 0-6 weeks has 5 wake windows of 45 minutes`() {
            val windows = useCase.getDefaultWakeWindows(3)
            assertEquals(5, windows.size)
            assertTrue(windows.all { it == Duration.ofMinutes(45) })
        }

        @Test
        fun `6-8 weeks has 4 graduated wake windows`() {
            val windows = useCase.getDefaultWakeWindows(7)
            assertEquals(4, windows.size)
            assertEquals(Duration.ofMinutes(60), windows[0])
            assertEquals(Duration.ofMinutes(75), windows[3])
        }

        @Test
        fun `2-3 months has 4 graduated wake windows`() {
            val windows = useCase.getDefaultWakeWindows(10)
            assertEquals(4, windows.size)
            assertEquals(Duration.ofMinutes(75), windows[0])
            assertEquals(Duration.ofMinutes(90), windows[3])
        }

        @Test
        fun `3-4 months has 3 graduated wake windows`() {
            val windows = useCase.getDefaultWakeWindows(14)
            assertEquals(3, windows.size)
            assertEquals(Duration.ofMinutes(90), windows[0])
            assertEquals(Duration.ofMinutes(120), windows[2])
        }

        @Test
        fun `4-6 months has 3 graduated wake windows`() {
            val windows = useCase.getDefaultWakeWindows(20)
            assertEquals(3, windows.size)
            assertEquals(Duration.ofMinutes(105), windows[0])
            assertEquals(Duration.ofMinutes(150), windows[2])
        }

        @Test
        fun `6-9 months has 3 graduated wake windows`() {
            val windows = useCase.getDefaultWakeWindows(30)
            assertEquals(3, windows.size)
            assertEquals(Duration.ofMinutes(150), windows[0])
            assertEquals(Duration.ofMinutes(210), windows[2])
        }

        @Test
        fun `9-12 months has 3 graduated wake windows`() {
            val windows = useCase.getDefaultWakeWindows(40)
            assertEquals(3, windows.size)
            assertEquals(Duration.ofMinutes(180), windows[0])
            assertEquals(Duration.ofMinutes(240), windows[2])
        }

        @Test
        fun `wake windows are graduated - first is shortest, last is longest`() {
            val ageBrackets = listOf(3, 7, 10, 14, 20, 30, 40)
            for (age in ageBrackets) {
                val windows = useCase.getDefaultWakeWindows(age)
                assertTrue(windows.first() <= windows.last(),
                    "Age $age weeks: first window should be <= last window")
            }
        }
    }

    // --- Nap Count ---

    @Nested
    inner class NapCountTests {
        @Test
        fun `newborn gets 4 naps`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(3))
            assertEquals(4, schedule.napTimes.size)
        }

        @Test
        fun `6 week old gets 3 naps`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(7))
            assertEquals(3, schedule.napTimes.size)
        }

        @Test
        fun `4 month old gets 2 naps`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(20))
            assertEquals(2, schedule.napTimes.size)
        }

        @Test
        fun `8 month old gets 2 naps`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(32))
            assertEquals(2, schedule.napTimes.size)
        }
    }

    // --- Bedtime Guardrails ---

    @Nested
    inner class BedtimeTests {
        @Test
        fun `newborn bedtime window is 9-11 PM`() {
            val window = useCase.getBedtimeWindow(3)
            assertEquals(LocalTime.of(21, 0), window.start)
            assertEquals(LocalTime.of(23, 0), window.endInclusive)
        }

        @Test
        fun `6-12 week bedtime window is 8-10 PM`() {
            val window = useCase.getBedtimeWindow(8)
            assertEquals(LocalTime.of(20, 0), window.start)
            assertEquals(LocalTime.of(22, 0), window.endInclusive)
        }

        @Test
        fun `3-4 month bedtime window is 7_30-9 PM`() {
            val window = useCase.getBedtimeWindow(14)
            assertEquals(LocalTime.of(19, 30), window.start)
            assertEquals(LocalTime.of(21, 0), window.endInclusive)
        }

        @Test
        fun `5-6 month bedtime window is 7-8 PM`() {
            val window = useCase.getBedtimeWindow(20)
            assertEquals(LocalTime.of(19, 0), window.start)
            assertEquals(LocalTime.of(20, 0), window.endInclusive)
        }

        @Test
        fun `7-12 month bedtime window is 6_30-8 PM`() {
            val window = useCase.getBedtimeWindow(30)
            assertEquals(LocalTime.of(18, 30), window.start)
            assertEquals(LocalTime.of(20, 0), window.endInclusive)
        }

        @Test
        fun `bedtime is clamped within window`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(30))
            val window = schedule.bedtimeWindow
            assertTrue(schedule.bedtime >= window.start,
                "Bedtime ${schedule.bedtime} should be >= ${window.start}")
            assertTrue(schedule.bedtime <= window.endInclusive,
                "Bedtime ${schedule.bedtime} should be <= ${window.endInclusive}")
        }
    }

    // --- Total Sleep Recommendation ---

    @Nested
    inner class TotalSleepTests {
        @Test
        fun `0-4 months recommends 14-17 hours`() {
            val rec = useCase.getTotalSleepRecommendation(10)
            assertEquals(Duration.ofHours(14), rec.start)
            assertEquals(Duration.ofHours(17), rec.endInclusive)
        }

        @Test
        fun `4-12 months recommends 12-16 hours`() {
            val rec = useCase.getTotalSleepRecommendation(20)
            assertEquals(Duration.ofHours(12), rec.start)
            assertEquals(Duration.ofHours(16), rec.endInclusive)
        }

        @Test
        fun `no logged data returns null totalSleepLogged`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(20))
            assertNull(schedule.totalSleepLogged)
        }
    }

    // --- Regression Detection ---

    @Nested
    inner class RegressionTests {
        @Test
        fun `4 month regression detected at 16 weeks`() {
            val info = useCase.detectRegression(16)
            assertNotNull(info)
            assertEquals("4-Month Sleep Regression", info!!.name)
        }

        @Test
        fun `4 month regression detected at 20 weeks`() {
            val info = useCase.detectRegression(20)
            assertNotNull(info)
            assertEquals("4-Month Sleep Regression", info!!.name)
        }

        @Test
        fun `8-10 month regression detected at 36 weeks`() {
            val info = useCase.detectRegression(36)
            assertNotNull(info)
            assertEquals("8-10 Month Sleep Regression", info!!.name)
        }

        @Test
        fun `12 month regression detected at 50 weeks`() {
            val info = useCase.detectRegression(50)
            assertNotNull(info)
            assertEquals("12-Month Sleep Regression", info!!.name)
        }

        @Test
        fun `no regression at 10 weeks`() {
            assertNull(useCase.detectRegression(10))
        }

        @Test
        fun `no regression at 26 weeks`() {
            assertNull(useCase.detectRegression(26))
        }

        @Test
        fun `no regression at 46 weeks`() {
            assertNull(useCase.detectRegression(46))
        }
    }

    // --- Personalization ---

    @Nested
    inner class PersonalizationTests {
        @Test
        fun `not personalized with fewer than 3 records`() = runTest {
            val records = listOf(
                createSleepRecord(SleepType.NAP, hoursAgo = 5, durationMin = 60),
                createSleepRecord(SleepType.NAP, hoursAgo = 3, durationMin = 45)
            )
            coEvery { sleepRepository.getCompletedRecordsSince(any()) } returns records
            coEvery { breastfeedingRepository.getLastSession() } returns null

            val schedule = useCase(babyOfAge(20))
            assertFalse(schedule.isPersonalized)
        }

        @Test
        fun `personalized with 3 or more nap records`() = runTest {
            // Create records with realistic spacing (2-3 hour gaps) so wake windows are valid
            val baseTime = Instant.now().minus(2, ChronoUnit.DAYS)
            val records = listOf(
                SleepRecord(id = 1, startTime = baseTime, endTime = baseTime.plus(Duration.ofMinutes(60)), sleepType = SleepType.NAP),
                SleepRecord(id = 2, startTime = baseTime.plus(Duration.ofHours(3)), endTime = baseTime.plus(Duration.ofHours(4)), sleepType = SleepType.NAP),
                SleepRecord(id = 3, startTime = baseTime.plus(Duration.ofHours(7)), endTime = baseTime.plus(Duration.ofHours(8)), sleepType = SleepType.NAP),
                SleepRecord(id = 4, startTime = baseTime.plus(Duration.ofHours(26)), endTime = baseTime.plus(Duration.ofHours(27)), sleepType = SleepType.NAP),
                SleepRecord(id = 5, startTime = baseTime.plus(Duration.ofHours(29)), endTime = baseTime.plus(Duration.ofHours(30)), sleepType = SleepType.NAP)
            )
            coEvery { sleepRepository.getCompletedRecordsSince(any()) } returns records
            coEvery { breastfeedingRepository.getLastSession() } returns null

            val schedule = useCase(babyOfAge(20))
            assertTrue(schedule.isPersonalized)
        }

        @Test
        fun `last feed time is populated when available`() = runTest {
            setupEmptyData()
            val feedTime = Instant.now().minus(2, ChronoUnit.HOURS)
            coEvery { breastfeedingRepository.getLastSession() } returns BreastfeedingSession(
                id = 1,
                startTime = feedTime,
                endTime = feedTime.plus(Duration.ofMinutes(15)),
                startingSide = BreastSide.LEFT
            )

            val schedule = useCase(babyOfAge(20))
            assertEquals(feedTime, schedule.lastFeedTime)
        }

        @Test
        fun `last feed time is null when no sessions`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(20))
            assertNull(schedule.lastFeedTime)
        }
    }

    // --- Full Schedule Integration ---

    @Nested
    inner class IntegrationTests {
        @Test
        fun `schedule has correct structure for newborn`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(2))

            assertEquals(ScheduleMode.DEMAND_DRIVEN, schedule.mode)
            assertEquals(4, schedule.napTimes.size)
            assertFalse(schedule.isPersonalized)
            assertNotNull(schedule.bedtimeWindow)
            assertNotNull(schedule.totalSleepRecommendation)
        }

        @Test
        fun `schedule has correct structure for 8 month old`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(32))

            assertEquals(ScheduleMode.CLOCK_ALIGNED, schedule.mode)
            assertEquals(2, schedule.napTimes.size)
            assertNotNull(schedule.regressionWarning)
            assertEquals("8-10 Month Sleep Regression", schedule.regressionWarning!!.name)
        }

        @Test
        fun `nap times are in chronological order`() = runTest {
            setupEmptyData()
            val schedule = useCase(babyOfAge(20), wakeUpTime = LocalTime.of(7, 0))

            for (i in 0 until schedule.napTimes.size - 1) {
                assertTrue(schedule.napTimes[i].startTime < schedule.napTimes[i + 1].startTime,
                    "Nap ${i + 1} should be before nap ${i + 2}")
            }
        }

        @Test
        fun `custom wake time shifts entire schedule`() = runTest {
            setupEmptyData()
            val scheduleEarly = useCase(babyOfAge(20), wakeUpTime = LocalTime.of(6, 0))
            val scheduleLate = useCase(babyOfAge(20), wakeUpTime = LocalTime.of(8, 0))

            assertTrue(scheduleEarly.napTimes[0].startTime < scheduleLate.napTimes[0].startTime)
        }
    }

    // --- Helpers ---

    private fun createSleepRecord(
        type: SleepType,
        hoursAgo: Long = 5,
        durationMin: Long = 60
    ): SleepRecord {
        val start = Instant.now().minus(hoursAgo, ChronoUnit.HOURS)
        return SleepRecord(
            id = 0,
            startTime = start,
            endTime = start.plus(Duration.ofMinutes(durationMin)),
            sleepType = type
        )
    }
}
