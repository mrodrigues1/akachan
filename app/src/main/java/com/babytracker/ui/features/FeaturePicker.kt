package com.babytracker.ui.features

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.babytracker.domain.model.AppFeature
import com.babytracker.domain.model.FeatureDomain
import com.babytracker.ui.component.BreastfeedingIcon
import com.babytracker.ui.component.PumpingIcon
import com.babytracker.ui.component.SleepIcon

/**
 * Stateless picker for the set of enabled [AppFeature]s, grouped by [FeatureDomain]. Single-feature
 * domains render as one switch; multi-feature domains add an expand affordance revealing per-tracker
 * switches. Toggle decisions are delegated upward; this composable never enforces the ">=1 enabled"
 * invariant itself. Shared by onboarding and the Settings "What you track" screen.
 */
@Composable
fun FeaturePicker(
    enabledFeatures: Set<AppFeature>,
    onFeatureToggled: (AppFeature, Boolean) -> Unit,
    onDomainToggled: (FeatureDomain, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FeatureDomain.entries.forEach { domain ->
            DomainCard(
                domain = domain,
                enabledFeatures = enabledFeatures,
                onFeatureToggled = onFeatureToggled,
                onDomainToggled = onDomainToggled,
            )
        }
    }
}

@Composable
private fun DomainCard(
    domain: FeatureDomain,
    enabledFeatures: Set<AppFeature>,
    onFeatureToggled: (AppFeature, Boolean) -> Unit,
    onDomainToggled: (FeatureDomain, Boolean) -> Unit,
) {
    val enabledCount = domain.features.count { it in enabledFeatures }
    val domainOn = enabledCount > 0
    var expanded by rememberSaveable(domain) { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DomainIcon(domain = domain)
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = domain.title, style = MaterialTheme.typography.titleMedium)
                    if (!domain.isSingleFeature) {
                        Text(
                            text = "$enabledCount of ${domain.features.size} on",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!domain.isSingleFeature) {
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) {
                                "Hide ${domain.title} trackers"
                            } else {
                                "Customize ${domain.title} trackers"
                            },
                        )
                    }
                }
                Switch(
                    checked = domainOn,
                    onCheckedChange = { on -> onDomainToggled(domain, on) },
                    modifier = Modifier
                        .testTag("feature_domain_switch_${domain.name}")
                        .semantics { stateDescription = if (domainOn) "On" else "Off" },
                )
            }
            if (!domain.isSingleFeature) {
                AnimatedVisibility(visible = expanded) {
                    Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        domain.features.forEach { feature ->
                            FeatureRow(
                                feature = feature,
                                checked = feature in enabledFeatures,
                                onCheckedChange = { on -> onFeatureToggled(feature, on) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(
    feature: AppFeature,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeatureIcon(feature = feature)
        Text(
            text = feature.label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier
                .testTag("feature_switch_${feature.name}")
                .semantics { stateDescription = if (checked) "On" else "Off" },
        )
    }
}

@Composable
private fun FeatureIcon(feature: AppFeature) {
    when (feature) {
        AppFeature.BREASTFEEDING -> BreastfeedingIcon(modifier = Modifier.size(24.dp))
        AppFeature.PUMPING -> PumpingIcon(modifier = Modifier.size(24.dp))
        AppFeature.SLEEP -> SleepIcon(modifier = Modifier.size(24.dp))
        else -> Text(text = feature.emoji, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun DomainIcon(domain: FeatureDomain) {
    if (domain == FeatureDomain.SLEEP) {
        SleepIcon(modifier = Modifier.size(28.dp))
    } else {
        Text(text = domain.emoji, style = MaterialTheme.typography.titleLarge)
    }
}

private val AppFeature.label: String
    get() = when (this) {
        AppFeature.BREASTFEEDING -> "Breastfeeding"
        AppFeature.BOTTLE_FEED -> "Bottle feed"
        AppFeature.PUMPING -> "Pumping"
        AppFeature.INVENTORY -> "Inventory"
        AppFeature.SLEEP -> "Sleep"
        AppFeature.DIAPERS -> "Diapers"
        AppFeature.GROWTH -> "Growth"
        AppFeature.MILESTONES -> "Milestones"
    }

private val AppFeature.emoji: String
    get() = when (this) {
        AppFeature.BREASTFEEDING -> ""
        AppFeature.BOTTLE_FEED -> "🍼"
        AppFeature.PUMPING -> ""
        AppFeature.INVENTORY -> "🧊"
        AppFeature.SLEEP -> ""
        AppFeature.DIAPERS -> "🧷"
        AppFeature.GROWTH -> "📈"
        AppFeature.MILESTONES -> "🎉"
    }

private val FeatureDomain.emoji: String
    get() = when (this) {
        FeatureDomain.FEEDING -> "🍼"
        FeatureDomain.SLEEP -> ""
        FeatureDomain.DIAPERS -> "🧷"
        FeatureDomain.GROWTH_DEVELOPMENT -> "📈"
    }
