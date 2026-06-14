package com.babytracker.data.growth

import android.content.Context
import com.babytracker.domain.growth.LmsPoint
import com.babytracker.domain.growth.WhoReferenceData
import com.babytracker.domain.model.BabySex
import com.babytracker.domain.model.GrowthType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads WHO LMS tables from bundled JSON assets under `assets/who/`, parsing
 * each file once and caching the result in memory.
 */
@Singleton
class AssetWhoReferenceData @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json,
) : WhoReferenceData {

    private val cache = ConcurrentHashMap<String, List<LmsPoint>>()

    override suspend fun lmsTable(type: GrowthType, sex: BabySex): List<LmsPoint> {
        val fileName = assetFileName(type, sex) ?: return emptyList()
        cache[fileName]?.let { return it }
        val parsed = withContext(Dispatchers.IO) {
            context.assets.open("who/$fileName").use { stream ->
                json.decodeFromString<List<LmsPoint>>(stream.readBytes().decodeToString())
            }
        }
        return cache.getOrPut(fileName) { parsed }
    }

    private fun assetFileName(type: GrowthType, sex: BabySex): String? {
        val sexPart = when (sex) {
            BabySex.MALE -> "male"
            BabySex.FEMALE -> "female"
            BabySex.UNSPECIFIED -> return null
        }
        val typePart = when (type) {
            GrowthType.WEIGHT -> "weight"
            GrowthType.LENGTH -> "length"
            GrowthType.HEAD_CIRC -> "head_circ"
        }
        return "${typePart}_$sexPart.json"
    }
}
