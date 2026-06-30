package com.babytracker.ui.onboarding.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.babytracker.R

@Composable
fun WelcomeStepContent(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalDensity.current.fontScale
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isCompactHeight = maxHeight < 560.dp || maxWidth > maxHeight
            val usesLargeText = fontScale >= 1.5f
            val horizontalPadding = if (isCompactHeight) 20.dp else 24.dp
            val heroHeight = when {
                isCompactHeight && usesLargeText -> 96.dp
                isCompactHeight -> 140.dp
                else -> 220.dp
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = horizontalPadding)
                    .padding(top = if (isCompactHeight) 16.dp else 32.dp, bottom = if (isCompactHeight) 16.dp else 28.dp),
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    WelcomeHero(heroHeight = heroHeight)
                    Spacer(modifier = Modifier.height(if (isCompactHeight) 18.dp else 32.dp))
                    Text(
                        text = stringResource(R.string.onboarding_welcome_title),
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.onboarding_welcome_subtitle),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(if (isCompactHeight) 20.dp else 28.dp))
                    WelcomeFeatureList()
                    Spacer(modifier = Modifier.height(24.dp))
                }
                Text(
                    text = stringResource(R.string.onboarding_cover_reassurance),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onGetStarted,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("onboarding_welcome_primary_action"),
                    shape = MaterialTheme.shapes.extraLarge,
                ) {
                    Text(stringResource(R.string.onboarding_get_started))
                }
            }
        }
    }
}

@Composable
private fun WelcomeHero(
    heroHeight: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(heroHeight)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.size(heroHeight),
        )
    }
}

@Composable
private fun WelcomeFeatureList(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        WelcomeFeatureRow(
            label = stringResource(R.string.onboarding_feature_one_handed),
            supportingText = stringResource(R.string.onboarding_feature_one_handed_desc),
        )
        WelcomeFeatureRow(
            label = stringResource(R.string.onboarding_feature_local),
            supportingText = stringResource(R.string.onboarding_feature_local_desc),
        )
        WelcomeFeatureRow(
            label = stringResource(R.string.onboarding_feature_partner),
            supportingText = stringResource(R.string.onboarding_feature_partner_desc),
        )
    }
}

@Composable
private fun WelcomeFeatureRow(
    label: String,
    supportingText: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(10.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
        ) {}
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
