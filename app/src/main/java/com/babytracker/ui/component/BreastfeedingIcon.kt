package com.babytracker.ui.component

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.babytracker.R

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
