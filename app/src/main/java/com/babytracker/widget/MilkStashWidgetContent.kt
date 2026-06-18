package com.babytracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
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
import com.babytracker.R
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.util.mlToOz
import java.util.Locale
import kotlin.math.roundToInt

private const val MILK_EMOJI = "🧊"
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

private fun bagCountLabel(context: Context, bagCount: Int): String =
    context.resources.getQuantityString(R.plurals.widget_milk_stash_bag_count, bagCount, bagCount)

/**
 * Neutral state shown when the Inventory feature is turned off. Tapping still opens Inventory so a
 * parent can re-enable it; tracked data is untouched.
 */
@Composable
private fun MilkStashOffContent() {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surfaceVariant)
            .clickable(openInventoryAction())
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = MILK_EMOJI,
            style = TextStyle(fontSize = 22.sp),
        )
        Spacer(modifier = GlanceModifier.height(6.dp))
        Text(
            text = context.getString(R.string.widget_milk_stash_off),
            maxLines = 2,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
            ),
        )
    }
}

/** 1×1 milk stash tile: compact volume hero over bag count, centered. Whole tile opens Inventory. */
@Composable
fun MilkStashSmallContent(data: MilkStashWidgetData, modifier: GlanceModifier = GlanceModifier) {
    if (!data.inventoryEnabled) {
        MilkStashOffContent()
        return
    }
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surfaceVariant)
            .clickable(openInventoryAction())
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (data.isEmpty()) {
            Text(
                text = context.getString(R.string.widget_milk_stash_no_milk),
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                ),
            )
            Text(
                text = context.getString(R.string.widget_milk_stash_tap_plus),
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                ),
            )
        } else {
            Text(
                text = formatVolume(data.totalMl, data.volumeUnit),
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                ),
            )
            Text(
                text = bagCountLabel(context, data.bagCount),
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                ),
            )
        }
    }
}

/** 1×2 (tall, narrow) milk stash tile: emoji header, large volume hero, and bag count. */
@Composable
fun MilkStashTallContent(data: MilkStashWidgetData, modifier: GlanceModifier = GlanceModifier) {
    if (!data.inventoryEnabled) {
        MilkStashOffContent()
        return
    }
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surfaceVariant)
            .clickable(openInventoryAction())
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = MILK_EMOJI,
            style = TextStyle(fontSize = 26.sp),
        )
        Spacer(modifier = GlanceModifier.height(2.dp))
        Text(
            text = context.getString(R.string.widget_milk_stash_title),
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            ),
        )
        Spacer(modifier = GlanceModifier.height(14.dp))
        MilkStashTallBody(data, context)
        Spacer(modifier = GlanceModifier.defaultWeight())
        if (!data.isEmpty()) {
            Text(
                text = bagCountLabel(context, data.bagCount),
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                ),
            )
        }
    }
}

/** Volume hero for the tall tile: empty prompt or the populated number/unit pair. */
@Composable
private fun MilkStashTallBody(data: MilkStashWidgetData, context: Context) {
    if (data.isEmpty()) {
        Text(
            text = context.getString(R.string.widget_milk_stash_no_milk),
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
            ),
        )
        Text(
            text = context.getString(R.string.widget_milk_stash_tap_add),
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
            ),
        )
    } else {
        Text(
            text = volumeNumber(data.totalMl, data.volumeUnit),
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
            ),
        )
        Text(
            text = volumeUnitLabel(context, data.totalMl, data.volumeUnit),
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
            ),
        )
    }
}

/** 2×1 (wide, short) milk stash tile: small emoji, volume hero, and bag count, all on one row. */
@Composable
fun MilkStashWideContent(data: MilkStashWidgetData, modifier: GlanceModifier = GlanceModifier) {
    if (!data.inventoryEnabled) {
        MilkStashOffContent()
        return
    }
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surfaceVariant)
            .clickable(openInventoryAction())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = MILK_EMOJI,
            style = TextStyle(fontSize = 20.sp),
        )
        Spacer(modifier = GlanceModifier.width(10.dp))
        if (data.isEmpty()) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = context.getString(R.string.widget_milk_stash_no_milk),
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    ),
                )
                Text(
                    text = context.getString(R.string.widget_milk_stash_tap_add_milk),
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                    ),
                )
            }
        } else {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = formatVolume(data.totalMl, data.volumeUnit),
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                    ),
                )
                Text(
                    text = bagCountLabel(context, data.bagCount),
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 12.sp,
                    ),
                )
            }
        }
    }
}

/** 2×2 milk stash tile: routes to the populated or empty layout. */
@Composable
fun MilkStashMediumContent(data: MilkStashWidgetData) {
    when {
        !data.inventoryEnabled -> MilkStashOffContent()
        data.isEmpty() -> MilkStashMediumEmpty()
        else -> MilkStashMediumFilled(data)
    }
}

/** Populated 2×2 tile: titled header, large volume hero, unit, and bag count row. */
@Composable
private fun MilkStashMediumFilled(data: MilkStashWidgetData) {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surfaceVariant)
            .clickable(openInventoryAction())
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = MILK_EMOJI,
                style = TextStyle(fontSize = 22.sp),
            )
            Spacer(modifier = GlanceModifier.width(6.dp))
            Text(
                text = context.getString(R.string.widget_milk_stash_title),
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                ),
            )
        }
        Spacer(modifier = GlanceModifier.defaultWeight())
        Text(
            text = volumeNumber(data.totalMl, data.volumeUnit),
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 48.sp,
            ),
        )
        Text(
            text = volumeUnitLabel(context, data.totalMl, data.volumeUnit),
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 16.sp,
            ),
        )
        Spacer(modifier = GlanceModifier.height(10.dp))
        Text(
            text = bagCountLabel(context, data.bagCount),
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            ),
        )
        Spacer(modifier = GlanceModifier.defaultWeight())
    }
}

@Composable
private fun MilkStashMediumEmpty() {
    val context = LocalContext.current
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surfaceVariant)
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
            text = context.getString(R.string.widget_milk_stash_empty),
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            ),
        )
        Text(
            text = context.getString(R.string.widget_milk_stash_tap_add_milk),
            maxLines = 1,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
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
private fun volumeUnitLabel(context: Context, totalMl: Int, unit: VolumeUnit): String = when (unit) {
    VolumeUnit.OZ -> context.getString(R.string.widget_milk_stash_total_oz)
    VolumeUnit.ML -> if (totalMl >= ML_PER_L) {
        context.getString(R.string.widget_milk_stash_total_l)
    } else {
        context.getString(R.string.widget_milk_stash_total_ml)
    }
}
