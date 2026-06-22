package com.babytracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.babytracker.domain.model.AppFeature
import com.babytracker.domain.repository.FeatureToggleRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeatureToggleRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : FeatureToggleRepository {

    override fun getEnabledFeatures(): Flow<Set<AppFeature>> =
        dataStore.data.map { AppFeature.deserialize(it[ENABLED_FEATURES]) }.distinctUntilChanged()

    override suspend fun setEnabledFeatures(features: Set<AppFeature>) {
        dataStore.edit { it[ENABLED_FEATURES] = AppFeature.serialize(features) }
    }

    private companion object {
        val ENABLED_FEATURES = stringPreferencesKey("enabled_features")
    }
}
