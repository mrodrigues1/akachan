package com.babytracker.ui.bottlefeed

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.model.MilkBag
import com.babytracker.ui.theme.BabyTrackerTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class BottleFeedSheetTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val baseNow = Instant.ofEpochSecond(1_700_000_000L)

    private val bag = MilkBag(
        id = 1,
        collectionDate = baseNow,
        volumeMl = 120,
        createdAt = baseNow,
    )

    private fun bag(
        id: Long,
        volumeMl: Int,
    ) = MilkBag(
        id = id,
        collectionDate = baseNow,
        volumeMl = volumeMl,
        createdAt = baseNow,
    )

    private fun state(
        feedType: FeedType = FeedType.BREAST_MILK,
        activeBags: List<MilkBag> = emptyList(),
        volumeText: String = "",
    ) = BottleFeedUiState(
        feedType = feedType,
        volumeText = volumeText,
        timestamp = baseNow,
        activeBags = activeBags,
    )

    private fun setSheet(
        state: BottleFeedUiState,
        onConfirm: () -> Unit = {},
    ) {
        composeRule.setContent {
            BabyTrackerTheme {
                BottleFeedSheet(
                    state = state,
                    onTypeChange = {},
                    onVolumeChange = {},
                    onTimeChange = {},
                    onBagSelect = {},
                    onNotesChange = {},
                    onConfirm = onConfirm,
                    onDismiss = {},
                )
            }
        }
    }

    @Test
    fun rendersFeedTypeOptionsAndVolumeField() {
        setSheet(state())

        composeRule.onNodeWithText("Breast milk").assertIsDisplayed()
        composeRule.onNodeWithText("Formula").assertIsDisplayed()
        composeRule.onNodeWithText("Volume (mL)").assertIsDisplayed()
    }

    @Test
    fun bagPickerShownForBreastMilkWithActiveBags() {
        setSheet(state(feedType = FeedType.BREAST_MILK, activeBags = listOf(bag)))

        composeRule.onNodeWithText("FROM STASH BAG (OPTIONAL)").assertIsDisplayed()
    }

    @Test
    fun bagPickerHiddenForFormula() {
        setSheet(state(feedType = FeedType.FORMULA, activeBags = listOf(bag)))

        composeRule.onAllNodesWithText("FROM STASH BAG (OPTIONAL)").assertCountEquals(0)
    }

    @Test
    fun bagPickerLimitsVisibleRowsAndScrollsAdditionalBags() {
        val bags = (1L..6L).map { id -> bag(id = id, volumeMl = 100 + id.toInt()) }
        setSheet(state(feedType = FeedType.BREAST_MILK, activeBags = bags))

        composeRule.onNodeWithTag(BAG_PICKER_LIST_TAG)
            .assert(hasScrollAction())
            .assertHeightIsEqualTo(248.dp)
            .performScrollToNode(hasText("106 ml", substring = true))

        composeRule.onNodeWithText("106 ml", substring = true).assertIsDisplayed()
    }

    @Test
    fun tapSaveInvokesOnConfirm() {
        var confirmed = false
        setSheet(state(volumeText = "120"), onConfirm = { confirmed = true })

        composeRule.onNodeWithText("Save feed").performClick()

        composeRule.runOnIdle { assertTrue(confirmed) }
    }
}
