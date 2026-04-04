package com.babytracker.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.babytracker.domain.model.AllergyType
import com.babytracker.domain.model.Baby
import com.babytracker.domain.repository.BabyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BabyRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : BabyRepository {

    private companion object {
        val BABY_NAME = stringPreferencesKey("baby_name")
        val BABY_BIRTH_DATE = longPreferencesKey("baby_birth_date")
        val ALLERGIES = stringPreferencesKey("allergies")
    }

    override fun getBaby(): Flow<Baby?> = dataStore.data.map { prefs ->
        val name = prefs[BABY_NAME] ?: return@map null
        val birthDate = prefs[BABY_BIRTH_DATE] ?: return@map null
        val allergies = prefs[ALLERGIES]
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.map { AllergyType.valueOf(it.trim()) }
            ?: emptyList()

        Baby(
            name = name,
            birthDate = Instant.ofEpochMilli(birthDate),
            allergies = allergies
        )
    }

    override suspend fun saveBaby(baby: Baby) {
        dataStore.edit { prefs ->
            prefs[BABY_NAME] = baby.name
            prefs[BABY_BIRTH_DATE] = baby.birthDate.toEpochMilli()
            prefs[ALLERGIES] = baby.allergies.joinToString(",") { it.name }
        }
    }
}
