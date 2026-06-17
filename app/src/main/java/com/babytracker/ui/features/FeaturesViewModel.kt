package com.babytracker.ui.features

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.AppFeature
import com.babytracker.domain.model.FeatureDomain
import com.babytracker.domain.usecase.features.GetEnabledFeaturesUseCase
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
    getEnabledFeatures: GetEnabledFeaturesUseCase,
    private val setFeatureEnabled: SetFeatureEnabledUseCase,
    private val setDomainEnabled: SetDomainEnabledUseCase,
) : ViewModel() {

    private val hint = MutableStateFlow(false)

    val uiState: StateFlow<FeaturesUiState> = combine(
        getEnabledFeatures(),
        hint,
    ) { features, showHint ->
        FeaturesUiState(enabledFeatures = features, showLastFeatureHint = showHint)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
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
}
