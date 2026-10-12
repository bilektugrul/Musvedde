package dev.aliarslan.musvedde.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface UpdateResult {
    data class Available(val version: String, val pageUrl: String, val apkUrl: String?) : UpdateResult
    data object UpToDate : UpdateResult
    data object NoReleases : UpdateResult
    data class Failed(val message: String) : UpdateResult
}

private fun parseVersion(text: String): List<Int>? =
    text.trim().removePrefix("v").removePrefix("V").split('.').map { it.toIntOrNull() ?: return null }

fun isNewerVersion(latest: String, current: String): Boolean {
    val a = parseVersion(latest) ?: return false
    val b = parseVersion(current) ?: return false
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}

class UpdateChecker(
    private val client: OkHttpClient,
    private val apiBaseUrl: HttpUrl,
    private val repository: String,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun check(currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        val url = apiBaseUrl.newBuilder().addPathSegments("repos/$repository/releases/latest").build()
        val request = Request.Builder().url(url).header("Accept", "application/vnd.github+json").build()
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 404 -> UpdateResult.NoReleases
                    !response.isSuccessful -> UpdateResult.Failed("HTTP ${response.code}")
                    else -> {
                        val release = json.decodeFromString<Release>(response.body.string())
                        if (isNewerVersion(release.tagName, currentVersion)) {
                            UpdateResult.Available(
                                version = release.tagName.removePrefix("v"),
                                pageUrl = release.htmlUrl,
                                apkUrl = release.assets.firstOrNull { it.name.endsWith(".apk") }?.downloadUrl,
                            )
                        } else {
                            UpdateResult.UpToDate
                        }
                    }
                }
            }
        } catch (e: Exception) {
            UpdateResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    @Serializable
    private data class Release(
        @SerialName("tag_name") val tagName: String,
        @SerialName("html_url") val htmlUrl: String,
        val assets: List<Asset> = emptyList(),
    )

    @Serializable
    private data class Asset(val name: String, @SerialName("browser_download_url") val downloadUrl: String)
}
