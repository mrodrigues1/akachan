package com.babytracker.ui.milestone

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.babytracker.domain.model.Milestone
import com.babytracker.domain.model.MilestoneAchievement
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.repository.MilestoneRepository
import com.babytracker.domain.usecase.milestone.DeleteMilestoneUseCase
import com.babytracker.domain.usecase.milestone.GetMilestoneProgressUseCase
import com.babytracker.domain.usecase.milestone.LogMilestoneUseCase
import com.babytracker.ui.theme.BabyTrackerTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class MilestonesScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val achievements = MutableStateFlow<List<MilestoneAchievement>>(emptyList())

    private val repository = object : MilestoneRepository {
        override suspend fun logAchievement(achievement: MilestoneAchievement): Long {
            achievements.value = achievements.value.filterNot { it.milestone == achievement.milestone } +
                achievement.copy(id = achievements.value.size + 1L)
            return achievements.value.size.toLong()
        }

        override fun getAchievements(): Flow<List<MilestoneAchievement>> = achievements

        override suspend fun deleteAchievement(milestone: Milestone) {
            achievements.value = achievements.value.filterNot { it.milestone == milestone }
        }
    }

    private fun viewModel() = MilestonesViewModel(
        GetMilestoneProgressUseCase(repository),
        LogMilestoneUseCase(repository),
        DeleteMilestoneUseCase(repository),
        object : MilestonePhotoCleaner {
            override suspend fun delete(photoUri: String?) = Unit
        },
    )

    @Test
    fun loggingAMilestoneShowsAchievedDate() {
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                MilestonesScreen(onNavigateBack = {}, viewModel = viewModel())
            }
        }

        composeRule.onNodeWithTag("milestone_log_SITTING_WITHOUT_SUPPORT").assertIsDisplayed()
        composeRule.onNodeWithTag("milestone_card_SITTING_WITHOUT_SUPPORT").performClick()
        composeRule.onNodeWithTag("milestone_save").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Achieved", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Achieved", substring = true)[0].assertIsDisplayed()
    }
}
