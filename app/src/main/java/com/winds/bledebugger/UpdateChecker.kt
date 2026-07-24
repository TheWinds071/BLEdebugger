package com.winds.bledebugger

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

data class ReleaseUpdate(
    val tagName: String,
    val releaseNotes: String,
    val releaseUrl: String
)

object UpdateChecker {
    private const val MAX_RELEASE_NOTES_LENGTH = 20_000
    private const val LATEST_RELEASE_API =
        "https://api.github.com/repos/TheWinds071/BLEdebugger/releases/latest"
    private const val RELEASES_PAGE =
        "https://github.com/TheWinds071/BLEdebugger/releases"

    suspend fun findAvailableUpdate(context: Context): ReleaseUpdate? = withContext(Dispatchers.IO) {
        val connection = runCatching {
            URL(LATEST_RELEASE_API).openConnection() as HttpsURLConnection
        }.getOrNull() ?: return@withContext null

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 6_000
            connection.readTimeout = 6_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "BLEdebugger-Android")

            if (connection.responseCode != HttpsURLConnection.HTTP_OK) {
                return@withContext null
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            val release = JSONObject(response)
            val tagName = release.optString("tag_name").trim()
            val currentVersion = context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
                .orEmpty()

            if (!isNewerVersion(tagName, currentVersion)) {
                return@withContext null
            }

            ReleaseUpdate(
                tagName = tagName,
                releaseNotes = release.optString("body")
                    .trim()
                    .take(MAX_RELEASE_NOTES_LENGTH),
                releaseUrl = release.optString("html_url", RELEASES_PAGE)
                    .takeIf { it.startsWith("https://github.com/") }
                    ?: RELEASES_PAGE
            )
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}

internal fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
    val remoteParts = parseVersion(remoteVersion) ?: return false
    val currentParts = parseVersion(currentVersion) ?: return false
    val partCount = maxOf(remoteParts.size, currentParts.size)

    for (index in 0 until partCount) {
        val remote = remoteParts.getOrElse(index) { 0 }
        val current = currentParts.getOrElse(index) { 0 }
        if (remote != current) return remote > current
    }
    return false
}

private fun parseVersion(version: String): List<Int>? {
    val normalized = version.trim().removePrefix("v").removePrefix("V").substringBefore('-')
    if (normalized.isBlank()) return null

    val parts = normalized.split('.').map { part ->
        part.takeWhile(Char::isDigit).takeIf { it.isNotEmpty() }?.toIntOrNull()
            ?: return null
    }
    return parts.takeIf { it.isNotEmpty() }
}
