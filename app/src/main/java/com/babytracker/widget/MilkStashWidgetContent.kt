package com.babytracker.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.util.mlToOz
import java.util.Locale
import kotlin.math.roundToInt

private const val MILK_EMOJI = "🥛"
private const val MILK_STASH_TITLE = "Milk Stash"
private const val ML_PER_L = 1000

/** Numeric ounce value with one decimal, trailing ".0" dropped ("28.4", "10"). */
private fun ozNumber(totalMl: Int): String {
    val rounded = (mlToOz(totalMl) * 10).roundToInt() / 10.0
    return if (rounded % 1.0 == 0.0) {
        rounded.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", rounded)
    }
}

/**
 * Formats a stash volume for the small tile in the user's [unit].
 * ML: liters with one decimal once it reaches 1000 mL, otherwise raw mL. OZ: "X.X oz".
 */
internal fun formatVolume(totalMl: Int, unit: VolumeUnit): String = when (unit) {
    VolumeUnit.OZ -> "${ozNumber(totalMl)} oz"
    VolumeUnit.ML -> if (totalMl >= ML_PER_L) {
        String.format(Locale.US, "%.1f L", totalMl / ML_PER_L.toFloat())
    } else {
        "$totalMl mL"
    }
}

private fun MilkStashWidgetData.isEmpty(): Boolean = totalMl == 0 && bagCount == 0

private fun bagCountLabel(bagCount: Int): String = if (bagCount == 1) "1 bag" else "$bagCount bags"

/** 1×1 milk stash tile: emoji, volume hero, and bag count. Whole tile opens Inventory. */
@Composable
fun MilkStashSmallContent(data: MilkStashWidgetData, modifier: GlanceModifier = GlanceModifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GlanceTheme.colors.tertiaryContainer)
            .clickable(openInventoryAction())
            .padding(12.dp),
    ) {
        Text(
            text = MILK_EMOJI,
            style = TextStyle(fontSize = 16.sp),
        )
        Spacer(modifier = GlanceModifier.defaultWeight())
        if (data.isEmpty()) {
            Text(
                text = "No milk",
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onTertiaryContainer,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                ),
            )
            Text(
                text = "Tap to add",
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onTertiaryContainer,
                    fontSize = 11.sp,
                ),
            )
        } else {
            Text(
                text = formatVolume(data.totalMl, data.volumeUnit),
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onTertiaryContainer,
                    fontWeight = FontWeight.Medium,
                    fontSize = 24.sp,
                ),
            )
            Text(
                text = bagCountLabel(data.bagCount),
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onTertiaryContainer,
                    fontSize = 12.sp,
                ),
            )
        }
    }
}

/** 2×2 milk stash tile: routes to the populated or empty layout. */
@Composable
fun MilkStashMediumContent(data: MilkStashWidgetData) {
    if (data.isEmpty()) {
        MilkStashMediumEmpty()
    } else {
        MilkStashMediumFilled(data)
    }
}

/** Populated 2×2 tile: titled header, large volume hero, unit, and bag count row. */
@Composable
private fun MilkStashMediumFilled(data: MilkStashWidgetData) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.tertiaryContainer)
            .clickable(openInventoryAction())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = MILK_EMOJI,
                style = TextStyle(fontSize = 20.sp),
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = MILK_STASH_TITLE,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onTertiaryContainer,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                ),
            )
        }
        Spacer(modifier = GlanceModifier.defaultWeight())
        Text(
            text = volumeNumber(data.totalMl, data.volumeUnit),
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onTertiaryContainer,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
            ),
        )
        Text(
            text = volumeUnitLabel(data.totalMl, data.volumeUnit),
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onTertiaryContainer,
                fontSize = 14.sp,
            ),
        )
        Spacer(modifier = GlanceModifier.height(8.dp))
        Text(
            text = bagCountLabel(data.bagCount),
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onTertiaryContainer,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
            ),
        )
    }
}

@Composable
private fun MilkStashMediumEmpty() {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.tertiaryContainer)
            .clickable(openInventoryAction())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = MILK_EMOJI,
            style = TextStyle(fontSize = 28.sp),
        )
        Spacer(modifier = GlanceModifier.height(6.dp))
        Text(
            text = "Stash is empty",
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onTertiaryContainer,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            ),
        )
        Text(
            text = "Tap to add milk",
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onTertiaryContainer,
                fontSize = 11.sp,
            ),
        )
    }
}

/** Numeric portion of the medium hero: "840" for mL, "1.5" for liters, or "28.4" for ounces. */
private fun volumeNumber(totalMl: Int, unit: VolumeUnit): String = when (unit) {
    VolumeUnit.OZ -> ozNumber(totalMl)
    VolumeUnit.ML -> if (totalMl >= ML_PER_L) {
        String.format(Locale.US, "%.1f", totalMl / ML_PER_L.toFloat())
    } else {
        totalMl.toString()
    }
}

/** Unit label sitting under the medium hero number. */
private fun volumeUnitLabel(totalMl: Int, unit: VolumeUnit): String = when (unit) {
    VolumeUnit.OZ -> "oz total"
    VolumeUnit.ML -> if (totalMl >= ML_PER_L) "L total" else "mL total"
}
