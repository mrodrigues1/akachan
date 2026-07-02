package com.babytracker.ui.features

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.AppFeature
import com.babytracker.domain.model.FeatureDomain
import com.babytracker.domain.repository.FeatureToggleRepository
import com.babytracker.domain.usecase.features.SetDomainEnabledUseCase
import com.babytracker.domain.usecase.features.SetFeatureEnabledUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FeaturesUiState(
    val enabledFeatures: Set<AppFeature> = AppFeature.ALL,
    val showLastFeatureHint: Boolean = false,
)

@HiltViewModel
class FeaturesViewModel @Inject constructor(
    featureToggleRepository: FeatureToggleRepository,
    private val setFeatureEnabled: SetFeatureEnabledUseCase,
    private val setDomainEnabled: SetDomainEnabledUseCase,
) : ViewModel() {

    private val hint = MutableStateFlow(false)

    val uiState: StateFlow<FeaturesUiState> = combine(
        featureToggleRepository.getEnabledFeatures(),
        hint,
    ) { features, showHint ->
        FeaturesUiState(enabledFeatures = features, showLastFeatureHint = showHint)
    }.stateIn(
        scope = viewModelScope,
        // WhileSubscribed releases the combine upstream + DataStore collector 5s after the
        // Features screen leaves; the screen always subscribes while shown, so behavior is
        // unchanged. Matches every other ViewModel in the app.
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = FeaturesUiState(),
    )

    fun onFeatureToggled(feature: AppFeature, enabled: Boolean) {
        viewModelScope.launch {
            val applied = setFeatureEnabled(feature, enabled)
            if (!applied && !enabled) hint.value = true
        }
    }

    fun onDomainToggled(domain: FeatureDomain, enabled: Boolean) {
        viewModelScope.launch {
            val applied = setDomainEnabled(domain, enabled)
            if (!applied && !enabled) hint.value = true
        }
    }

    fun onHintShown() {
        hint.value = false
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
