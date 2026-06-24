package com.babytracker.ui.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.babytracker.R
import com.babytracker.domain.model.BabyEventType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val EaseOutQuart = CubicBezierEasing(0.25f, 1f, 0.5f, 1f)

@get:DrawableRes
private val BabyEventType.iconRes: Int
	get() = when (this) {
		BabyEventType.SLEEPY_CUE -> R.drawable.ic_cue_sleepy
		BabyEventType.HUNGER_CUE -> R.drawable.ic_cue_hungry
		BabyEventType.FUSSY -> R.drawable.ic_cue_fussy
		BabyEventType.SICK -> R.drawable.ic_cue_sick
		BabyEventType.TEETHING -> R.drawable.ic_cue_teething
		BabyEventType.TRAVEL -> R.drawable.ic_cue_travel
	}

@get:StringRes
private val BabyEventType.labelRes: Int
	get() = when (this) {
		BabyEventType.SLEEPY_CUE -> R.string.cue_sleepy
		BabyEventType.HUNGER_CUE -> R.string.cue_hungry
		BabyEventType.FUSSY -> R.string.cue_fussy
		BabyEventType.SICK -> R.string.cue_sick
		BabyEventType.TEETHING -> R.string.cue_teething
		BabyEventType.TRAVEL -> R.string.cue_travel
	}

@Composable
fun CueQuickTapRow(
	onCueTapped: (BabyEventType) -> Unit,
	modifier: Modifier = Modifier,
) {
	var lastTappedLabel by remember { mutableStateOf<String?>(null) }
	val confirmScope = rememberCoroutineScope()
	val confirmJob = remember { arrayOfNulls<Job>(1) }
	val cueLabels = BabyEventType.entries.associateWith { stringResource(it.labelRes) }

	Column(modifier = modifier) {
		Text(
			text = stringResource(R.string.cue_log_a_cue),
			style = MaterialTheme.typography.labelMedium,
			color = MaterialTheme.colorScheme.secondary,
		)
		Spacer(Modifier.height(4.dp))
		Row(
			modifier = Modifier
				.fillMaxWidth()
				.horizontalScroll(rememberScrollState()),
			horizontalArrangement = Arrangement.spacedBy(8.dp),
		) {
			BabyEventType.entries.forEach { type ->
				CueChip(
					type = type,
					label = cueLabels.getValue(type),
					onTapped = { tapped ->
						onCueTapped(tapped)
						confirmJob[0]?.cancel()
						lastTappedLabel = cueLabels.getValue(tapped)
						confirmJob[0] = confirmScope.launch {
							delay(2_000L)
							lastTappedLabel = null
						}
					},
				)
			}
		}
		AnimatedVisibility(
			visible = lastTappedLabel != null,
			enter = fadeIn(tween(160, easing = EaseOutQuart)),
			exit = fadeOut(tween(200, easing = EaseOutQuart)),
		) {
			Text(
				text = stringResource(R.string.cue_logged, lastTappedLabel ?: ""),
				style = MaterialTheme.typography.bodySmall,
				color = MaterialTheme.colorScheme.onSurfaceVariant,
				modifier = Modifier.padding(top = 6.dp),
			)
		}
	}
}

@Composable
private fun CueChip(
	type: BabyEventType,
	label: String,
	onTapped: (BabyEventType) -> Unit,
) {
	var selected by remember { mutableStateOf(false) }
	var showLeadingIcon by remember { mutableStateOf(false) }
	var tapCount by remember { mutableIntStateOf(0) }
	val scaleAnim = remember { Animatable(1f) }
	val haptic = LocalHapticFeedback.current
	val scope = rememberCoroutineScope()
	val deselectionJob = remember { arrayOfNulls<Job>(1) }

	val checkAlpha by animateFloatAsState(
		targetValue = if (selected) 1f else 0f,
		animationSpec = tween(if (selected) 160 else 200),
		label = "check-alpha-${type.name}",
		finishedListener = { if (!selected) showLeadingIcon = false },
	)
	val checkScale by animateFloatAsState(
		targetValue = if (selected) 1f else 0f,
		animationSpec = tween(durationMillis = if (selected) 160 else 180, easing = EaseOutQuart),
		label = "check-scale-${type.name}",
	)

	LaunchedEffect(selected) {
		if (selected) showLeadingIcon = true
	}

	LaunchedEffect(tapCount) {
		if (tapCount > 0) {
			scaleAnim.stop()
			scaleAnim.animateTo(0.80f, tween(65, easing = EaseOutQuart))
			scaleAnim.animateTo(1.25f, tween(190, easing = EaseOutQuart))
			scaleAnim.animateTo(0.94f, tween(110, easing = EaseOutQuart))
			scaleAnim.animateTo(1.0f, tween(130, easing = EaseOutQuart))
		}
	}

	FilterChip(
		selected = selected,
		onClick = {
			haptic.performHapticFeedback(HapticFeedbackType.LongPress)
			onTapped(type)
			tapCount++
			selected = true
			deselectionJob[0]?.cancel()
			deselectionJob[0] = scope.launch {
				delay(1_500L)
				selected = false
			}
		},
		leadingIcon = if (showLeadingIcon) {
			{
				Icon(
					imageVector = Icons.Filled.Check,
					contentDescription = null,
					modifier = Modifier
						.size(16.dp)
						.scale(checkScale)
						.alpha(checkAlpha),
				)
			}
		} else {
			null
		},
		label = {
			Row(
				horizontalArrangement = Arrangement.spacedBy(6.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				Image(
					painter = painterResource(type.iconRes),
					contentDescription = null,
					modifier = Modifier
						.size(30.dp)
						.clearAndSetSemantics {},
				)
				Text(
					text = label,
					style = MaterialTheme.typography.labelMedium,
				)
			}
		},
		modifier = Modifier
			.heightIn(min = 44.dp)
			.scale(scaleAnim.value)
			.animateContentSize(tween(durationMillis = 180, easing = EaseOutQuart)),
	)
}
