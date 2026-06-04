package com.babytracker.domain.model

sealed class SleepPredictionState {
    data class Window(val window: SleepWindow) : SleepPredictionState()
    data class NeedMoreData(val progress: EvidenceProgress) : SleepPredictionState()
    data object CueLed : SleepPredictionState()
    data object CurrentlySleeping : SleepPredictionState()
    data object AfterActiveFeed : SleepPredictionState()
    data object Overdue : SleepPredictionState()
    data class Unavailable(val reason: String) : SleepPredictionState()
}
