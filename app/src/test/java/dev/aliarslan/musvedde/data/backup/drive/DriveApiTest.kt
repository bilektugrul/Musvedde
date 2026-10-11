package dev.aliarslan.musvedde.data.backup.drive

import com.google.common.truth.Truth.assertThat
import dev.aliarslan.musvedde.data.backup.BackupAuthException
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

class DriveApiTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val server = MockWebServer().apply { start() }
    private val api = DriveApi(OkHttpClient(), server.url("/"))

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun listParsesFiles() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"files":[{"id":"abc","name":"musvedde-backup-20261011-090507.zip","size":"1234","modifiedTime":"2026-10-11T09:05:08.000Z"}]}""",
            ),
        )

        val files = api.list("token")

        val file = files.single()
        assertThat(file.ref).isEqualTo("abc")
        assertThat(file.name).isEqualTo("musvedde-backup-20261011-090507.zip")
        assertThat(file.sizeBytes).isEqualTo(1234)
        assertThat(file.modifiedAt).isEqualTo(1_791_709_508_000L)
        val request = server.takeRequest()
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer token")
        assertThat(request.requestUrl!!.queryParameter("spaces")).isEqualTo("appDataFolder")
    }

    @Test
    fun uploadSendsAppDataParent() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":"new"}"""))
        val file = tmp.newFile("b.zip").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        api.upload("token", "musvedde-backup-x.zip", file)

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.requestUrl!!.encodedPath).isEqualTo("/upload/drive/v3/files")
        assertThat(request.requestUrl!!.queryParameter("uploadType")).isEqualTo("multipart")
        val body = request.body.readUtf8()
        assertThat(body).contains("\"parents\":[\"appDataFolder\"]")
        assertThat(body).contains("musvedde-backup-x.zip")
    }

    @Test
    fun unauthorizedIsAuthError() {
        server.enqueue(MockResponse().setResponseCode(401))

        assertThrows(BackupAuthException::class.java) {
            kotlinx.coroutines.runBlocking { api.list("expired") }
        }
    }

    @Test
    fun serverErrorIsTransient() {
        server.enqueue(MockResponse().setResponseCode(503))

        val error = assertThrows(IOException::class.java) {
            kotlinx.coroutines.runBlocking { api.delete("token", "abc") }
        }
        assertThat(error).isNotInstanceOf(BackupAuthException::class.java)
    }
}
