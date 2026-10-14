package dev.aliarslan.musvedde.web

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import dev.aliarslan.musvedde.core.Clock
import dev.aliarslan.musvedde.data.backup.RecordingLogger
import dev.aliarslan.musvedde.data.db.inMemoryDatabase
import dev.aliarslan.musvedde.data.db.WebNoteRow
import dev.aliarslan.musvedde.data.note.LabelRepository
import dev.aliarslan.musvedde.data.note.NoteRepository
import dev.aliarslan.musvedde.data.note.VersionRepository
import io.ktor.client.HttpClient
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WebApiTest {
    private val db = inMemoryDatabase()
    private var now = 1_000_000L
    private val clock = Clock { now }
    private val notes = NoteRepository(db, clock = clock, io = Dispatchers.Unconfined)
    private val labels = LabelRepository(db.labelDao(), Dispatchers.Unconfined)
    private val versions = VersionRepository(db, clock, Dispatchers.Unconfined)
    private val auth = WebAuth(db.webDeviceDao(), clock)
    private val sync = WebNoteSync(firstRevision = 1_000)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private var importedStream: Int = 0

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun environment() = WebEnvironment(
        notes = notes,
        labels = labels,
        versions = versions,
        auth = auth,
        sync = sync,
        theme = flowOf(WebTheme(dark = false, colors = emptyMap(), noteColors = emptyMap())),
        locale = { "tr" },
        appVersion = "test",
        importKeep = { input ->
            importedStream = input.readBytes().size
            throw IllegalStateException("not a zip")
        },
        asset = { path -> if (path == "index.html") "<html></html>".toByteArray() else null },
        expectedHost = { "localhost:80" },
        closing = MutableSharedFlow(),
        logger = RecordingLogger(),
    )

    private fun web(block: suspend ApplicationTestBuilder.(HttpClient) -> Unit) = testApplication {
        scope.launch { sync.follow(db.noteDao().observeWebNotes().map { rows -> rows.map(WebNoteRow::toWeb) }) }
        application { webEditor(environment()) }
        block(newClient())
    }

    private fun ApplicationTestBuilder.newClient() = createClient {
        install(HttpCookies)
        defaultRequest { header(HttpHeaders.Host, "localhost:80") }
    }

    private suspend fun HttpClient.send(method: String, path: String, body: String? = null): HttpResponse {
        val build: io.ktor.client.request.HttpRequestBuilder.() -> Unit = {
            header(HttpHeaders.Origin, "http://localhost:80")
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        return when (method) {
            "POST" -> post(path, build)
            "PATCH" -> patch(path, build)
            else -> error(method)
        }
    }

    private suspend fun HttpClient.pairDevice() {
        val response = send("POST", "/api/pair", """{"code":"${auth.pairingCode.value}","name":"Firefox · Linux"}""")
        assertWithMessage(response.bodyAsText()).that(response.status).isEqualTo(HttpStatusCode.OK)
    }

    private suspend fun HttpClient.bootstrap(): JsonObject = json.parseToJsonElement(get("/api/bootstrap").bodyAsText()).jsonObject

    private fun JsonObject.noteIds() = getValue("notes").jsonArray.map { it.jsonObject.getValue("id").jsonPrimitive.long }

    private suspend fun eventually(check: suspend () -> Boolean) {
        repeat(200) {
            if (check()) return
            withContext(Dispatchers.IO) { Thread.sleep(10) }
        }
        error("condition never became true")
    }

    @Test
    fun theApiNeedsAPairedComputer() = web { client ->
        assertThat(client.get("/api/bootstrap").status).isEqualTo(HttpStatusCode.Unauthorized)
        assertThat(client.send("POST", "/api/pair", """{"code":"nope","name":"x"}""").status).isEqualTo(HttpStatusCode.Forbidden)

        client.pairDevice()

        assertThat(client.get("/api/bootstrap").status).isEqualTo(HttpStatusCode.OK)
        assertThat(db.webDeviceDao().observeAll().first().single().name).isEqualTo("Firefox · Linux")
    }

    @Test
    fun theCodeChangesAfterPairingSoItWorksOnlyOnce() = web { client ->
        val code = auth.pairingCode.value
        client.pairDevice()

        val second = newClient()
        assertThat(second.send("POST", "/api/pair", """{"code":"$code","name":"x"}""").status).isEqualTo(HttpStatusCode.Forbidden)
    }

    @Test
    fun repeatedWrongCodesPausePairing() = web { client ->
        repeat(WebAuth.MAX_FAILURES) { client.send("POST", "/api/pair", """{"code":"000000x","name":"x"}""") }

        val response = client.send("POST", "/api/pair", """{"code":"${auth.pairingCode.value}","name":"x"}""")

        assertThat(response.status).isEqualTo(HttpStatusCode.TooManyRequests)
        assertThat(response.bodyAsText()).contains("\"retryAfterSeconds\":30")
        now += 30_000
        client.pairDevice()
    }

    @Test
    fun otherHostsAndOtherSitesAreRefused() = web { client ->
        client.pairDevice()

        assertThat(client.get("/api/bootstrap") { header(HttpHeaders.Host, "evil.example") }.status).isEqualTo(HttpStatusCode.Forbidden)
        assertThat(client.post("/api/notes") { contentType(ContentType.Application.Json); setBody("""{"title":"a","body":"b"}""") }.status)
            .isEqualTo(HttpStatusCode.Forbidden)
        assertThat(
            client.post("/api/notes") {
                header(HttpHeaders.Origin, "http://evil.example")
                contentType(ContentType.Application.Json)
                setBody("""{"title":"a","body":"b"}""")
            }.status,
        ).isEqualTo(HttpStatusCode.Forbidden)
        assertThat(notes.observe(dev.aliarslan.musvedde.data.note.NoteFilter.InState(dev.aliarslan.musvedde.domain.NoteState.ACTIVE)).first())
            .isEmpty()
    }

    @Test
    fun aRemovedComputerIsLockedOut() = web { client ->
        client.pairDevice()
        auth.remove(db.webDeviceDao().observeAll().first().single().id)

        assertThat(client.get("/api/bootstrap").status).isEqualTo(HttpStatusCode.Unauthorized)
    }

    @Test
    fun secretNotesNeverReachTheWeb() = web { client ->
        val open = notes.create("açık", "deniz")
        val secret = notes.create("sır", "deniz")
        notes.setLocked(listOf(secret), true)
        client.pairDevice()
        eventually { sync.snapshot().first.map { it.id } == listOf(open) }

        assertThat(client.bootstrap().noteIds()).containsExactly(open)
        assertThat(client.send("PATCH", "/api/notes/$secret", """{"title":"x","body":"y","baseUpdatedAt":0,"force":true}""").status)
            .isEqualTo(HttpStatusCode.NotFound)
        assertThat(client.send("POST", "/api/notes/$secret/session").status).isEqualTo(HttpStatusCode.NotFound)
        client.send("POST", "/api/notes/bulk", """{"ids":[$open,$secret],"action":"trash"}""")

        assertThat(notes.get(secret)!!.state.name).isEqualTo("ACTIVE")
        assertThat(notes.get(open)!!.state.name).isEqualTo("TRASHED")
        assertThat(client.get("/api/notes?since=1000").bodyAsText()).doesNotContain("sır")
    }

    @Test
    fun browsersCatchUpWithOnlyWhatChanged() = web { client ->
        val kept = notes.create("kalır", "")
        val moved = notes.create("gizlenecek", "")
        val changed = notes.create("eski", "")
        client.pairDevice()
        eventually { sync.snapshot().first.size == 3 }
        val revision = client.bootstrap().getValue("revision").jsonPrimitive.long

        notes.setLocked(listOf(moved), true)
        notes.setColor(listOf(changed), dev.aliarslan.musvedde.domain.NoteColor.RED)
        eventually { sync.since(revision)!!.let { it.deletedIds.isNotEmpty() && it.notes.isNotEmpty() } }

        val diff = json.parseToJsonElement(client.get("/api/notes?since=$revision").bodyAsText()).jsonObject
        assertThat(diff.noteIds()).containsExactly(changed)
        assertThat(diff.getValue("deletedIds").jsonArray.map { it.jsonPrimitive.long }).containsExactly(moved)
        assertThat(diff.noteIds()).doesNotContain(kept)
        assertThat(client.get("/api/notes?since=5").status).isEqualTo(HttpStatusCode.Gone)
    }

    @Test
    fun editsMadeOnThePhoneMeanwhileAreAConflict() = web { client ->
        val id = notes.create("başlık", "ilk")
        client.pairDevice()
        val base = notes.get(id)!!.updatedAt
        client.send("POST", "/api/notes/$id/session")

        now += 1_000
        notes.updateContent(id, "başlık", "telefonda değişti")
        val conflict = client.send("PATCH", "/api/notes/$id", """{"title":"başlık","body":"web","baseUpdatedAt":$base}""")
        assertThat(conflict.status).isEqualTo(HttpStatusCode.Conflict)
        assertThat(conflict.bodyAsText()).contains("telefonda değişti")

        now += 1_000
        val forced = client.send("PATCH", "/api/notes/$id", """{"title":"başlık","body":"web","baseUpdatedAt":$base,"force":true}""")
        assertThat(forced.status).isEqualTo(HttpStatusCode.OK)
        assertThat(notes.get(id)!!.body).isEqualTo("web")
        assertThat(versions.observe(id).first().map { it.body }).contains("telefonda değişti")
    }

    @Test
    fun aColorChangeOnThePhoneIsNotAConflict() = web { client ->
        val id = notes.create("başlık", "ilk")
        client.pairDevice()
        eventually { sync.snapshot().first.isNotEmpty() }
        val base = notes.get(id)!!.updatedAt
        client.send("POST", "/api/notes/$id/session")

        now += 1_000
        notes.setColor(listOf(id), dev.aliarslan.musvedde.domain.NoteColor.BLUE)
        val response = client.send("PATCH", "/api/notes/$id", """{"title":"başlık","body":"web","baseUpdatedAt":$base}""")

        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(notes.get(id)!!.body).isEqualTo("web")
    }

    @Test
    fun newNotesTakeTheirColorAndLabels() = web { client ->
        val label = labels.create("Şiirler").getOrThrow()
        client.pairDevice()

        val response = client.send("POST", "/api/notes", """{"title":"t","body":"b","color":"TEAL","labelIds":[$label]}""")

        assertThat(response.status).isEqualTo(HttpStatusCode.Created)
        val note = notes.get(json.parseToJsonElement(response.bodyAsText()).jsonObject.getValue("id").jsonPrimitive.long)!!
        assertThat(note.color.name).isEqualTo("TEAL")
        assertThat(note.labelIds).containsExactly(label)
    }

    @Test
    fun unreadableTakeoutFilesChangeNothing() = web { client ->
        client.pairDevice()

        val response = client.post("/api/import/keep") {
            header(HttpHeaders.Origin, "http://localhost:80")
            contentType(ContentType.parse("application/zip"))
            setBody(ByteArray(10))
        }

        assertThat(response.status).isEqualTo(HttpStatusCode.BadRequest)
        assertThat(importedStream).isEqualTo(10)
    }

    @Test
    fun thePageIsServedWithoutPairing() = web { client ->
        val response = client.get("/")
        assertThat(response.status).isEqualTo(HttpStatusCode.OK)
        assertThat(response.bodyAsText()).contains("<html>")
        assertThat(client.get("/../secret").status).isEqualTo(HttpStatusCode.NotFound)
    }

    @Test
    fun syncReportsNothingForUnknownRevisions() = runBlocking {
        val local = WebNoteSync(firstRevision = 50)
        local.apply(emptyList())
        assertThat(local.since(49)).isNull()
        assertThat(local.since(51)).isNull()
        assertThat(local.since(50)!!.notes).isEmpty()
    }
}
