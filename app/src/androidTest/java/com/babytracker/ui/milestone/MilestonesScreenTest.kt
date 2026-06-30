package com.babytracker.ui.milestone

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.babytracker.domain.model.Milestone
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.repository.MilestoneRepository
import com.babytracker.domain.usecase.milestone.AddMilestoneUseCase
import com.babytracker.domain.usecase.milestone.DeleteMilestoneUseCase
import com.babytracker.domain.usecase.milestone.GetMilestonesUseCase
import com.babytracker.domain.usecase.milestone.UpdateMilestoneUseCase
import com.babytracker.ui.theme.BabyTrackerTheme
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class MilestonesScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val moments = MutableStateFlow<List<Milestone>>(emptyList())

    private val repository = object : MilestoneRepository {
        override fun getMilestones(): Flow<List<Milestone>> = moments
        override fun getMilestone(id: Long): Flow<Milestone?> =
            moments.map { list -> list.firstOrNull { it.id == id } }

        override suspend fun addMilestone(milestone: Milestone): Long {
            val id = moments.value.size + 1L
            moments.value = moments.value + milestone.copy(id = id)
            return id
        }

        override suspend fun updateMilestone(milestone: Milestone) {
            moments.value = moments.value.map { if (it.id == milestone.id) milestone else it }
        }

        override suspend fun deleteMilestone(id: Long) {
            moments.value = moments.value.filterNot { it.id == id }
        }
    }

    private fun viewModel() = MilestonesViewModel(
        GetMilestonesUseCase(repository),
        AddMilestoneUseCase(repository),
        UpdateMilestoneUseCase(repository),
        DeleteMilestoneUseCase(repository),
        object : MilestonePhotoCleaner {
            override suspend fun delete(photoUri: String?) = Unit
        },
        mockk(relaxed = true),
    )

    private fun setScreen() {
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                MilestonesScreen(onNavigateBack = {}, onNavigateToDetail = {}, viewModel = viewModel())
            }
        }
    }

    @Test
    fun emptyStateShownWhenNoMoments() {
        setScreen()

        composeRule.onNodeWithText("No moments yet").assertIsDisplayed()
    }

    @Test
    fun newestMomentIsPromotedToTheHero() {
        moments.value = listOf(
            Milestone(id = 1, title = "First giggle", date = LocalDate.of(2026, 6, 1)),
        )

        setScreen()

        composeRule.onNodeWithTag("milestone_hero_card").assertIsDisplayed()
        composeRule.onNodeWithText("First giggle").assertIsDisplayed()
    }

    @Test
    fun olderMomentsAreListedBelowTheHero() {
        moments.value = listOf(
            Milestone(id = 2, title = "First step", date = LocalDate.of(2026, 6, 2)),
            Milestone(id = 1, title = "First giggle", date = LocalDate.of(2026, 6, 1)),
        )

        setScreen()

        // Newest is the hero; the older one falls into the timeline below it.
        composeRule.onNodeWithTag("milestone_hero_card").assertIsDisplayed()
        composeRule.onNodeWithTag("moment_card_1").assertIsDisplayed()
    }

    @Test
    fun tappingAddOpensTheEditor() {
        setScreen()

        composeRule.onNodeWithTag("milestone_add").performClick()

        composeRule.onNodeWithText("New moment").assertIsDisplayed()
        composeRule.onNodeWithTag("milestone_title").assertIsDisplayed()
    }
}
