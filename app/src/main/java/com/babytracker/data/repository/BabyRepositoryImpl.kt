package com.babytracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.babytracker.domain.model.AllergyType
import com.babytracker.domain.model.Baby
import com.babytracker.domain.repository.BabyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BabyRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : BabyRepository {

    private object Keys {
        val BABY_NAME = stringPreferencesKey("baby_name")
        val BABY_BIRTH_DATE = longPreferencesKey("baby_birth_date")
        val ALLERGIES = stringPreferencesKey("allergies")
        val CUSTOM_ALLERGY_NOTE = stringPreferencesKey("custom_allergy_note")
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }

    override fun getBabyProfile(): Flow<Baby?> = dataStore.data.map { prefs ->
        val name = prefs[Keys.BABY_NAME] ?: return@map null
        val epochDay = prefs[Keys.BABY_BIRTH_DATE] ?: return@map null
        Baby(
            name = name,
            birthDate = LocalDate.ofEpochDay(epochDay),
            allergies = prefs[Keys.ALLERGIES]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.map { AllergyType.valueOf(it) }
                ?: emptyList(),
            customAllergyNote = prefs[Keys.CUSTOM_ALLERGY_NOTE],
        )
    }

    override suspend fun saveBabyProfile(baby: Baby) {
        dataStore.edit { prefs ->
            prefs[Keys.BABY_NAME] = baby.name.trim()
            prefs[Keys.BABY_BIRTH_DATE] = baby.birthDate.toEpochDay()
            prefs[Keys.ALLERGIES] = baby.allergies.joinToString(",") { it.name }
            if (baby.customAllergyNote != null) {
                prefs[Keys.CUSTOM_ALLERGY_NOTE] = baby.customAllergyNote
            } else {
                prefs.remove(Keys.CUSTOM_ALLERGY_NOTE)
            }
            prefs[Keys.ONBOARDING_COMPLETE] = true
        }
    }

    override fun isOnboardingComplete(): Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_COMPLETE] ?: false
    }
}
