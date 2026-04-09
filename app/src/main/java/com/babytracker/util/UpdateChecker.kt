package com.babytracker.util

import android.util.Log
import com.babytracker.BuildConfig
import com.babytracker.domain.model.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateChecker @Inject constructor() {

    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/mrodrigues1/akachan/releases/latest")
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                connectTimeout = 5_000
                readTimeout = 5_000
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val json = JSONObject(connection.inputStream.bufferedReader().readText())
            val tagName = json.getString("tag_name")
            val releaseUrl = json.getString("html_url")
            if (isNewerVersion(tagName, BuildConfig.VERSION_NAME)) {
                UpdateInfo(versionName = tagName.trimStart('v'), releaseUrl = releaseUrl)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.d("UpdateChecker", "Update check failed: ${e.message}")
            null
        }
    }

    private fun isNewerVersion(tagName: String, currentVersion: String): Boolean {
        val remoteParts = tagName.trimStart('v').split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = currentVersion.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(remoteParts.size, currentParts.size)) {
            val remote = remoteParts.getOrElse(i) { 0 }
            val current = currentParts.getOrElse(i) { 0 }
            if (remote > current) return true
            if (remote < current) return false
        }
        return false
    }
}
