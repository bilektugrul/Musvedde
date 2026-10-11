package dev.aliarslan.musvedde.data.backup.drive

import dev.aliarslan.musvedde.data.backup.BackupAuthException
import dev.aliarslan.musvedde.data.backup.BackupFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.Instant

class DriveHttpException(val code: Int) : IOException("Google Drive returned HTTP $code")

class DriveApi(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL.toHttpUrl(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun list(token: String): List<BackupFile> = withContext(Dispatchers.IO) {
        val url = baseUrl.newBuilder()
            .addPathSegments("drive/v3/files")
            .addQueryParameter("spaces", APP_DATA)
            .addQueryParameter("fields", "files(id,name,size,modifiedTime)")
            .addQueryParameter("pageSize", "1000")
            .build()
        execute(token, Request.Builder().url(url).get()).use { response ->
            json.decodeFromString<FileList>(response.body.string()).files.map {
                BackupFile(
                    name = it.name,
                    sizeBytes = it.size?.toLongOrNull() ?: 0,
                    modifiedAt = it.modifiedTime?.let { time -> Instant.parse(time).toEpochMilli() } ?: 0,
                    ref = it.id,
                )
            }
        }
    }

    suspend fun upload(token: String, name: String, file: File) {
        withContext(Dispatchers.IO) {
            val url = baseUrl.newBuilder()
                .addPathSegments("upload/drive/v3/files")
                .addQueryParameter("uploadType", "multipart")
                .build()
            val metadata = json.encodeToString(UploadMetadata(name, listOf(APP_DATA)))
            val body = MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(metadata.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .addPart(file.asRequestBody("application/zip".toMediaType()))
                .build()
            execute(token, Request.Builder().url(url).post(body)).close()
        }
    }

    suspend fun download(token: String, id: String): InputStream = withContext(Dispatchers.IO) {
        val url = baseUrl.newBuilder()
            .addPathSegments("drive/v3/files")
            .addPathSegment(id)
            .addQueryParameter("alt", "media")
            .build()
        execute(token, Request.Builder().url(url).get()).body.byteStream()
    }

    suspend fun delete(token: String, id: String) {
        withContext(Dispatchers.IO) {
            val url = baseUrl.newBuilder().addPathSegments("drive/v3/files").addPathSegment(id).build()
            execute(token, Request.Builder().url(url).delete()).close()
        }
    }

    private fun execute(token: String, builder: Request.Builder): Response {
        val response = client.newCall(builder.header("Authorization", "Bearer $token").build()).execute()
        if (response.isSuccessful) return response
        response.close()
        if (response.code == 401 || response.code == 403) {
            throw BackupAuthException("Google Drive rejected the authorization (HTTP ${response.code})")
        }
        throw DriveHttpException(response.code)
    }

    @Serializable
    private data class FileList(val files: List<DriveFile> = emptyList())

    @Serializable
    private data class DriveFile(val id: String, val name: String, val size: String? = null, val modifiedTime: String? = null)

    @Serializable
    private data class UploadMetadata(val name: String, val parents: List<String>)

    companion object {
        const val DEFAULT_BASE_URL = "https://www.googleapis.com/"
        const val APP_DATA = "appDataFolder"
    }
}
