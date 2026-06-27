package com.babytracker.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.babytracker.R
import com.babytracker.domain.model.DiaperType

/**
 * Decorative section icon: draws [res] with cleared semantics so screen readers skip it. The named
 * section-icon composables below are thin aliases that fix [res], keeping call sites self-documenting.
 */
@Composable
fun SectionIcon(
    @DrawableRes res: Int,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        modifier = modifier.clearAndSetSemantics {},
    )
}

@Composable
fun BreastfeedingIcon(modifier: Modifier = Modifier) = SectionIcon(R.drawable.ic_breastfeeding_section, modifier)

@Composable
fun BottleFeedIcon(modifier: Modifier = Modifier) = SectionIcon(R.drawable.ic_bottle_feed_section, modifier)

@Composable
fun FeedingHistoryIcon(modifier: Modifier = Modifier) = SectionIcon(R.drawable.ic_feeding_history_section, modifier)

@Composable
fun SleepIcon(modifier: Modifier = Modifier) = SectionIcon(R.drawable.ic_sleep_section, modifier)

@Composable
fun NapIcon(modifier: Modifier = Modifier) = SectionIcon(R.drawable.ic_nap_section, modifier)

@Composable
fun PumpingIcon(modifier: Modifier = Modifier) = SectionIcon(R.drawable.ic_pumping_section, modifier)

@Composable
fun InventoryIcon(modifier: Modifier = Modifier) = SectionIcon(R.drawable.ic_inventory_section, modifier)

@Composable
fun DiaperIcon(modifier: Modifier = Modifier) = SectionIcon(R.drawable.ic_diaper_section, modifier)

@Composable
fun GrowthIcon(modifier: Modifier = Modifier) = SectionIcon(R.drawable.ic_growth_section, modifier)

@Composable
fun WeightIcon(modifier: Modifier = Modifier) = SectionIcon(R.drawable.ic_weight_section, modifier)

@Composable
fun LengthIcon(modifier: Modifier = Modifier) = SectionIcon(R.drawable.ic_length_section, modifier)

@Composable
fun HeadIcon(modifier: Modifier = Modifier) = SectionIcon(R.drawable.ic_head_section, modifier)

@Composable
fun TrendsIcon(modifier: Modifier = Modifier) = SectionIcon(R.drawable.ic_trends_section, modifier)

@Composable
fun MilestoneIcon(modifier: Modifier = Modifier) = SectionIcon(R.drawable.ic_milestone_section, modifier)

@Composable
fun VaccineIcon(modifier: Modifier = Modifier) = SectionIcon(R.drawable.ic_vaccine_section, modifier)

@Composable
fun DoctorVisitIcon(modifier: Modifier = Modifier) = SectionIcon(R.drawable.ic_doctor_visit_section, modifier)

@Composable
fun HomeGreetingHandIcon(modifier: Modifier = Modifier) = SectionIcon(R.drawable.ic_home_greeting_hand, modifier)

@Composable
fun DiaperTypeIcon(
    type: DiaperType,
    modifier: Modifier = Modifier,
) = SectionIcon(type.iconRes, modifier)

@get:DrawableRes
private val DiaperType.iconRes: Int
    get() = when (this) {
        DiaperType.WET -> R.drawable.ic_diaper_type_wet
        DiaperType.DIRTY -> R.drawable.ic_diaper_type_dirty
        DiaperType.BOTH -> R.drawable.ic_diaper_type_both
    }
