package dev.aliarslan.musvedde.data.update

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Test

class UpdateCheckerTest {
    private val server = MockWebServer().apply { start() }
    private val checker = UpdateChecker(OkHttpClient(), server.url("/"), repository = "owner/repo")

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun comparesVersionsNumerically() {
        assertThat(isNewerVersion("v0.10.0", "0.9.3")).isTrue()
        assertThat(isNewerVersion("0.4.1", "0.4.0")).isTrue()
        assertThat(isNewerVersion("v1.0", "0.9.9")).isTrue()
        assertThat(isNewerVersion("v0.4.0", "0.4.0")).isFalse()
        assertThat(isNewerVersion("v0.3.2", "0.4.0")).isFalse()
        assertThat(isNewerVersion("nonsense", "0.4.0")).isFalse()
    }

    @Test
    fun readsTheLatestReleaseAndItsApk() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {"tag_name":"v0.5.0","html_url":"https://github.com/owner/repo/releases/tag/v0.5.0",
                 "assets":[{"name":"notes.txt","browser_download_url":"https://x/notes.txt"},
                           {"name":"app-release.apk","browser_download_url":"https://x/app-release.apk"}]}
                """.trimIndent(),
            ),
        )

        val result = checker.check(currentVersion = "0.4.0")

        assertThat(result).isEqualTo(
            UpdateResult.Available(version = "0.5.0", pageUrl = "https://github.com/owner/repo/releases/tag/v0.5.0", apkUrl = "https://x/app-release.apk"),
        )
        assertThat(server.takeRequest().requestUrl!!.encodedPath).isEqualTo("/repos/owner/repo/releases/latest")
    }

    @Test
    fun sameVersionIsUpToDate() = runTest {
        server.enqueue(MockResponse().setBody("""{"tag_name":"v0.4.0","html_url":"u","assets":[]}"""))

        assertThat(checker.check(currentVersion = "0.4.0")).isEqualTo(UpdateResult.UpToDate)
    }

    @Test
    fun noPublishedReleaseIsReported() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))

        assertThat(checker.check(currentVersion = "0.4.0")).isEqualTo(UpdateResult.NoReleases)
    }

    @Test
    fun serverErrorsAreFailures() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))

        assertThat(checker.check(currentVersion = "0.4.0")).isInstanceOf(UpdateResult.Failed::class.java)
    }
}
