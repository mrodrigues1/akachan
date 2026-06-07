package com.babytracker.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
	val cues = remember { BabyEventType.entries }
	val tappedCues = remember { mutableStateSetOf<BabyEventType>() }
	val removalJobs = remember { HashMap<BabyEventType, Job>() }
	val scope = rememberCoroutineScope()

	Row(
		modifier = modifier
			.fillMaxWidth()
			.horizontalScroll(rememberScrollState()),
		horizontalArrangement = Arrangement.spacedBy(8.dp),
	) {
		cues.forEach { type ->
			val selected = type in tappedCues
			val scale by animateFloatAsState(
				targetValue = if (selected) 1.08f else 1.0f,
				animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
				label = "cue-chip-scale-${type.name}",
			)
			FilterChip(
				selected = selected,
				onClick = {
					onCueTapped(type)
					tappedCues.add(type)
					removalJobs[type]?.cancel()
					removalJobs[type] = scope.launch {
						delay(1_200L)
						tappedCues.remove(type)
						removalJobs.remove(type)
					}
				},
				label = {
					Text(
						text = "${type.emoji} ${type.label}",
						style = MaterialTheme.typography.labelMedium,
					)
				},
				modifier = Modifier.scale(scale),
			)
		}
	}
}
