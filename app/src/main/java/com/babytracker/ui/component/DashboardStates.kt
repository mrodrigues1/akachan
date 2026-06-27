package com.babytracker.ui.component

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.babytracker.R

/**
 * Calm placeholder shown until the first data emission lands, so a cold start no longer flashes an
 * empty state for a frame. Static low-emphasis blocks (no shimmer) keep it quiet. [heroHeight] sizes
 * the lead block to match the dashboard's hero card.
 */
@Composable
fun DashboardSkeleton(heroHeight: Dp, modifier: Modifier = Modifier) {
    val block = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val loadingLabel = stringResource(R.string.loading)
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            // Otherwise the placeholder blocks announce nothing; tell TalkBack the screen is loading.
            .semantics {
                contentDescription = loadingLabel
                liveRegion = LiveRegionMode.Polite
            },
    ) {
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(heroHeight)
                .clip(MaterialTheme.shapes.large)
                .background(block),
        )
        Spacer(Modifier.height(24.dp))
        repeat(3) { index ->
            Box(
                Modifier
                    .fillMaxWidth(if (index == 0) 0.4f else 1f)
                    .height(20.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(block),
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Centered load-error message with a retry action; announces itself to TalkBack on transition. */
@Composable
fun DashboardError(
    errorText: String,
    accent: Color,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            // Announce the failure on transition, not only when a screen reader lands on the text.
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = errorText
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = errorText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRetry) {
            Text(text = stringResource(R.string.try_again), color = accent)
        }
    }
}

/** Day-granularity countdown to an upcoming event: Today / Tomorrow / in N days. */
@Composable
fun countdownLabel(
    days: Int,
    @StringRes todayRes: Int,
    @StringRes tomorrowRes: Int,
    @PluralsRes pluralRes: Int,
): String = when {
    days <= 0 -> stringResource(todayRes)
    days == 1 -> stringResource(tomorrowRes)
    else -> pluralStringResource(pluralRes, days, days)
}
