package com.babytracker.ui.onboarding.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.babytracker.R
import com.babytracker.ui.onboarding.MAX_BABY_NAME_LENGTH
import com.babytracker.ui.onboarding.OnboardingStep

private const val COUNT_WARNING_THRESHOLD = 40

@Composable
fun NameStepContent(
    name: String,
    nameError: String?,
    isNextEnabled: Boolean,
    onNameChanged: (String) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val nameFocusRequester = remember { FocusRequester() }
    val nameLength = remember(name) { name.codePointCount(0, name.length) }

    LaunchedEffect(Unit) {
        nameFocusRequester.requestFocus()
    }

    OnboardingScaffold(
        modifier = modifier,
        currentStep = OnboardingStep.NAME,
        onBack = onBack,
        primaryLabel = stringResource(R.string.onboarding_continue),
        onPrimary = onNext,
        primaryEnabled = isNextEnabled,
        primaryTestTag = "onboarding_name_primary_action",
    ) {
        Text(
            text = stringResource(R.string.onboarding_name_question),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_name_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onNameChanged,
            label = { Text(stringResource(R.string.onboarding_baby_name_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    if (isNextEnabled) onNext()
                },
            ),
            isError = nameError != null,
            supportingText = nameSupportingText(error = nameError, length = nameLength),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(nameFocusRequester),
        )
    }
}

private fun nameSupportingText(
    error: String?,
    length: Int,
): (@Composable () -> Unit)? = when {
    error != null -> {
        { Text(error) }
    }
    length > COUNT_WARNING_THRESHOLD -> {
        { Text(stringResource(R.string.onboarding_char_count, length, MAX_BABY_NAME_LENGTH)) }
    }
    else -> null
}
