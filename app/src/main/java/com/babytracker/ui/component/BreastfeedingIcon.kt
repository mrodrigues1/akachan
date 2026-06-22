package com.babytracker.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.babytracker.R
import com.babytracker.domain.model.DiaperType

@Composable
fun BreastfeedingIcon(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_breastfeeding_section),
        contentDescription = null,
        modifier = modifier.clearAndSetSemantics {},
    )
}

@Composable
fun BottleFeedIcon(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_bottle_feed_section),
        contentDescription = null,
        modifier = modifier.clearAndSetSemantics {},
    )
}

@Composable
fun FeedingHistoryIcon(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_feeding_history_section),
        contentDescription = null,
        modifier = modifier.clearAndSetSemantics {},
    )
}

@Composable
fun SleepIcon(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_sleep_section),
        contentDescription = null,
        modifier = modifier.clearAndSetSemantics {},
    )
}

@Composable
fun PumpingIcon(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_pumping_section),
        contentDescription = null,
        modifier = modifier.clearAndSetSemantics {},
    )
}

@Composable
fun InventoryIcon(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_inventory_section),
        contentDescription = null,
        modifier = modifier.clearAndSetSemantics {},
    )
}

@Composable
fun DiaperIcon(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_diaper_section),
        contentDescription = null,
        modifier = modifier.clearAndSetSemantics {},
    )
}

@Composable
fun GrowthIcon(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_growth_section),
        contentDescription = null,
        modifier = modifier.clearAndSetSemantics {},
    )
}

@Composable
fun MilestoneIcon(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_milestone_section),
        contentDescription = null,
        modifier = modifier.clearAndSetSemantics {},
    )
}

@Composable
fun VaccineIcon(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.ic_vaccine_section),
        contentDescription = null,
        modifier = modifier.clearAndSetSemantics {},
    )
}

@Composable
fun DiaperTypeIcon(
    type: DiaperType,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(type.iconRes),
        contentDescription = null,
        modifier = modifier.clearAndSetSemantics {},
    )
}

@get:DrawableRes
private val DiaperType.iconRes: Int
    get() = when (this) {
        DiaperType.WET -> R.drawable.ic_diaper_type_wet
        DiaperType.DIRTY -> R.drawable.ic_diaper_type_dirty
        DiaperType.BOTH -> R.drawable.ic_diaper_type_both
    }
