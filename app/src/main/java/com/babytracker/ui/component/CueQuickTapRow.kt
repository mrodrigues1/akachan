package com.babytracker.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.babytracker.domain.model.BabyEventType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val BabyEventType.emoji: String
	get() = when (this) {
		BabyEventType.SLEEPY_CUE -> "😪"
		BabyEventType.HUNGER_CUE -> "😋"
		BabyEventType.FUSSY -> "😣"
		BabyEventType.SICK -> "🤒"
		BabyEventType.TEETHING -> "🦷"
		BabyEventType.TRAVEL -> "✈️"
	}

private val BabyEventType.label: String
	get() = when (this) {
		BabyEventType.SLEEPY_CUE -> "Sleepy"
		BabyEventType.HUNGER_CUE -> "Hungry"
		BabyEventType.FUSSY -> "Fussy"
		BabyEventType.SICK -> "Sick"
		BabyEventType.TEETHING -> "Teething"
		BabyEventType.TRAVEL -> "Travel"
	}

@Composable
fun CueQuickTapRow(
	onCueTapped: (BabyEventType) -> Unit,
	modifier: Modifier = Modifier,
) {
	Row(
		modifier = modifier
			.fillMaxWidth()
			.horizontalScroll(rememberScrollState()),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		BabyEventType.entries.forEach { type ->
			CueChip(type = type, onTapped = onCueTapped)
		}
	}
}

@Composable
private fun CueChip(
	type: BabyEventType,
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
		animationSpec = if (selected) {
			spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessHigh)
		} else {
			tween(durationMillis = 180)
		},
		label = "check-scale-${type.name}",
	)

	LaunchedEffect(selected) {
		if (selected) showLeadingIcon = true
	}

	LaunchedEffect(tapCount) {
		if (tapCount > 0) {
			scaleAnim.stop()
			scaleAnim.animateTo(0.80f, tween(65, easing = FastOutSlowInEasing))
			scaleAnim.animateTo(1.25f, tween(190, easing = FastOutSlowInEasing))
			scaleAnim.animateTo(0.94f, tween(110, easing = FastOutSlowInEasing))
			scaleAnim.animateTo(1.0f, tween(130, easing = FastOutSlowInEasing))
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
			Text(
				text = "${type.emoji} ${type.label}",
				style = MaterialTheme.typography.labelMedium,
			)
		},
		modifier = Modifier
			.scale(scaleAnim.value)
			.animateContentSize(tween(durationMillis = 180, easing = FastOutSlowInEasing)),
	)
}
