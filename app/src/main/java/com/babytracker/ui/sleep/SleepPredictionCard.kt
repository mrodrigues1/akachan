package com.babytracker.ui.sleep

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.babytracker.R
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepWindow
import com.babytracker.util.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SleepPredictionCard(
    state: SleepPredictionState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    var visibleState by remember { mutableStateOf(state) }
    if (state !is SleepPredictionState.Unavailable) {
        visibleState = state
    }

    val cardDescription = when (visibleState) {
        is SleepPredictionState.Window ->
            stringResource(R.string.sleep_prediction_card_cd_window, (visibleState as SleepPredictionState.Window).window.bestEstimate.formatTime())
        is SleepPredictionState.NeedMoreData ->
            stringResource(R.string.sleep_prediction_card_cd_need_more)
        SleepPredictionState.Overdue ->
            stringResource(R.string.sleep_prediction_card_cd_overdue)
        SleepPredictionState.CurrentlySleeping ->
            stringResource(R.string.sleep_prediction_card_cd_sleeping)
        SleepPredictionState.CueLed ->
            stringResource(R.string.sleep_prediction_card_cd_cue_led)
        SleepPredictionState.AfterActiveFeed ->
            stringResource(R.string.sleep_prediction_card_cd_after_feed)
        is SleepPredictionState.Unavailable ->
            stringResource(R.string.sleep_prediction_card_cd_default)
    }

    AnimatedVisibility(
        visible = state !is SleepPredictionState.Unavailable,
        enter = fadeIn(tween(200, easing = EaseOutQuart)),
        exit = fadeOut(tween(150, easing = EaseOutQuart)),
        modifier = modifier,
    ) {
        Card(
            onClick = onClick,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            ),
            shape = MaterialTheme.shapes.large,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = cardDescription },
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .animateContentSize(animationSpec = tween(200, easing = EaseOutQuart)),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Bedtime,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.sleep_prediction_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
                Spacer(Modifier.height(12.dp))
                AnimatedContent(
                    targetState = visibleState,
                    transitionSpec = {
                        fadeIn(tween(200, easing = EaseOutQuart)) togetherWith
                            fadeOut(tween(150, easing = EaseOutQuart))
                    },
                    contentKey = { it::class },
                    label = "SleepPredictionCardContent",
                ) { targetState ->
                    when (targetState) {
                        is SleepPredictionState.Window -> WindowCardContent(window = targetState.window)
                        is SleepPredictionState.NeedMoreData -> NeedMoreDataContent(progress = targetState.progress)
                        SleepPredictionState.Overdue -> OverdueContent()
                        SleepPredictionState.CueLed -> CueLedContent()
                        SleepPredictionState.CurrentlySleeping -> CurrentlySleepingContent()
                        SleepPredictionState.AfterActiveFeed -> AfterActiveFeedContent()
                        is SleepPredictionState.Unavailable -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun WindowCardContent(window: SleepWindow) {
    val confidenceLabelText = confidenceLabel(window.confidence)
    val aroundContentDescription =
        stringResource(R.string.sleep_prediction_cd_around, window.bestEstimate.formatTime())
    Column {
        Text(
            text = stringResource(R.string.sleep_prediction_around, window.bestEstimate.formatTime()),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.semantics {
                contentDescription = aroundContentDescription
            },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.sleep_prediction_window_range, window.windowStart.formatTime(), window.windowEnd.formatTime()),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = confidenceLabelText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
        )
    }
}
