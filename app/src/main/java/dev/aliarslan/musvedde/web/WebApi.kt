package dev.aliarslan.musvedde.web

import dev.aliarslan.musvedde.core.ErrorLogger
import dev.aliarslan.musvedde.data.db.WebDeviceEntity
import dev.aliarslan.musvedde.data.note.LabelRepository
import dev.aliarslan.musvedde.data.note.NoteRepository
import dev.aliarslan.musvedde.data.note.VersionRepository
import dev.aliarslan.musvedde.domain.Note
import dev.aliarslan.musvedde.domain.NoteColor
import io.ktor.http.ContentType
import io.ktor.http.Cookie
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.excludeContentType
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.minimumSize
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.sse.SSE
import io.ktor.server.sse.sse
import io.ktor.sse.ServerSentEvent
import io.ktor.util.AttributeKey
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

class WebEnvironment(
    val notes: NoteRepository,
    val labels: LabelRepository,
    val versions: VersionRepository,
    val auth: WebAuth,
    val sync: WebNoteSync,
    val theme: Flow<WebTheme>,
    val locale: () -> String,
    val appVersion: String,
    val importKeep: suspend (InputStream) -> ImportResult,
    val asset: (String) -> ByteArray?,
    /** The `host:port` browsers must use; requests naming any other host are refused. Null accepts any. */
    val expectedHost: () -> String?,
    /** Emits once when the session is about to stop. */
    val closing: Flow<Unit>,
    val logger: ErrorLogger,
)

const val DEVICE_COOKIE = "musvedde_device"
const val MAX_IMPORT_BYTES = 200L * 1024 * 1024
private const val HEARTBEAT_MS = 25_000L
private val DeviceKey = AttributeKey<WebDeviceEntity>("webDevice")
private val webJson = Json { ignoreUnknownKeys = true }

private val ApplicationCall.device: WebDeviceEntity get() = attributes[DeviceKey]

fun Application.webEditor(env: WebEnvironment) {
    val editing = EditingSessions(env.versions)

    install(ContentNegotiation) { json(webJson) }
    install(Compression) {
        gzip {
            minimumSize(1024)
            excludeContentType(ContentType.Text.EventStream)
        }
    }
    install(SSE)
    install(StatusPages) {
        exception<BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request")) }
        exception<Throwable> { call, cause ->
            env.logger.error("Web", "${call.request.httpMethod.value} ${call.request.path()} failed: ${cause.message}", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal"))
        }
    }
    install(guard(env))

    routing {
        post("/api/pair") {
            val request = call.receive<PairRequest>()
            when (val result = env.auth.pair(request.code, request.name)) {
                is PairResult.Paired -> {
                    call.response.cookies.append(
                        Cookie(
                            name = DEVICE_COOKIE,
                            value = result.token,
                            maxAge = 365 * 24 * 60 * 60,
                            path = "/",
                            httpOnly = true,
                            extensions = mapOf("SameSite" to "Strict"),
                        ),
                    )
                    call.respond(emptyMap<String, String>())
                }
                PairResult.WrongCode -> call.respond(HttpStatusCode.Forbidden, ErrorResponse("wrong_code"))
                is PairResult.Throttled -> call.respond(
                    HttpStatusCode.TooManyRequests,
                    ErrorResponse("throttled", retryAfterSeconds = (result.retryAfterMillis + 999) / 1000),
                )
            }
        }

        get("/api/bootstrap") {
            val (notes, revision) = env.sync.snapshot()
            call.respond(
                Bootstrap(
                    notes = notes,
                    labels = env.webLabels(),
                    revision = revision,
                    theme = env.theme.first(),
                    locale = env.locale(),
                    appVersion = env.appVersion,
                ),
            )
        }

        get("/api/notes") {
            val since = call.request.queryParameters["since"]?.toLongOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request"))
            val changes = env.sync.since(since)
                ?: return@get call.respond(HttpStatusCode.Gone, ErrorResponse("stale"))
            call.respond(changes)
        }

        get("/api/labels") { call.respond(env.webLabels()) }

        post("/api/notes") {
            val request = call.receive<NewNote>()
            val id = env.notes.create(request.title, request.body)
            request.color?.let { env.notes.setColor(listOf(id), NoteColor.fromName(it)) }
            request.labelIds.forEach { env.labels.setLabel(listOf(id), it, add = true) }
            call.respond(HttpStatusCode.Created, env.notes.get(id)!!.toWeb())
        }

        patch("/api/notes/{id}") {
            val note = env.visibleNote(call) ?: return@patch call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found"))
            val request = call.receive<NoteContent>()
            val device = call.device.id
            if (!request.force && editing.changedElsewhere(device, note, request.baseUpdatedAt)) {
                return@patch call.respond(HttpStatusCode.Conflict, ConflictResponse("conflict", note.toWeb()))
            }
            if (request.force && editing.changedElsewhere(device, note, request.baseUpdatedAt)) {
                env.versions.endSession(note.id)
                editing.forget(note.id)
            }
            editing.ensure(device, note)
            env.notes.updateContent(note.id, request.title, request.body)
            editing.saw(device, note.id, request.title, request.body)
            call.respond(env.notes.get(note.id)!!.toWeb())
        }

        post("/api/notes/{id}/session") {
            val note = env.visibleNote(call) ?: return@post call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found"))
            editing.ensure(call.device.id, note)
            call.respond(HttpStatusCode.NoContent)
        }

        delete("/api/notes/{id}/session") {
            val id = call.parameters["id"]?.toLongOrNull()
                ?: return@delete call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found"))
            editing.end(call.device.id, id)
            call.respond(HttpStatusCode.NoContent)
        }

        post("/api/notes/bulk") {
            val request = call.receive<BulkRequest>()
            val ids = env.sync.visible(request.ids)
            if (ids.isNotEmpty()) {
                val notes = env.notes
                when (request.action) {
                    "color" -> notes.setColor(ids, NoteColor.fromName(request.color ?: return@post call.badRequest()))
                    "addLabel" -> env.labels.setLabel(ids, request.labelId ?: return@post call.badRequest(), add = true)
                    "removeLabel" -> env.labels.setLabel(ids, request.labelId ?: return@post call.badRequest(), add = false)
                    "pin" -> notes.setPinnedToTop(ids, true)
                    "unpin" -> notes.setPinnedToTop(ids, false)
                    "archive" -> notes.archive(ids)
                    "unarchive" -> notes.unarchive(ids)
                    "trash" -> notes.trash(ids)
                    "restore" -> notes.restore(ids)
                    "deleteForever" -> notes.deleteForever(ids)
                    else -> return@post call.badRequest()
                }
            }
            call.respond(RevisionResponse(env.sync.revision.value))
        }

        post("/api/trash/empty") {
            env.notes.deleteForever(env.sync.snapshot().first.filter { it.state == "TRASHED" }.map { it.id })
            call.respond(emptyMap<String, String>())
        }

        post("/api/labels") {
            val name = call.receive<LabelName>().name
            if (name.isBlank()) return@post call.badRequest()
            env.labels.create(name).fold(
                onSuccess = { call.respond(HttpStatusCode.Created, WebLabel(it, name.trim())) },
                onFailure = { call.respond(HttpStatusCode.Conflict, ErrorResponse("exists")) },
            )
        }

        patch("/api/labels/{id}") {
            val id = call.parameters["id"]?.toLongOrNull() ?: return@patch call.badRequest()
            val name = call.receive<LabelName>().name
            if (name.isBlank()) return@patch call.badRequest()
            if (env.webLabels().none { it.id == id }) return@patch call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found"))
            env.labels.rename(id, name).fold(
                onSuccess = { call.respond(WebLabel(id, name.trim())) },
                onFailure = { call.respond(HttpStatusCode.Conflict, ErrorResponse("exists")) },
            )
        }

        delete("/api/labels/{id}") {
            val id = call.parameters["id"]?.toLongOrNull() ?: return@delete call.badRequest()
            env.labels.delete(id)
            call.respond(HttpStatusCode.NoContent)
        }

        post("/api/import/keep") {
            val length = call.request.header(HttpHeaders.ContentLength)?.toLongOrNull()
            if (length != null && length > MAX_IMPORT_BYTES) {
                return@post call.respond(HttpStatusCode.PayloadTooLarge, ErrorResponse("too_large"))
            }
            val input = LimitedInputStream(call.receiveChannel().toInputStream(), MAX_IMPORT_BYTES)
            val result = withContext(Dispatchers.IO) { runCatching { env.importKeep(input) } }
            result.fold(
                onSuccess = { call.respond(it) },
                onFailure = {
                    if (it is TooLargeException) {
                        call.respond(HttpStatusCode.PayloadTooLarge, ErrorResponse("too_large"))
                    } else {
                        env.logger.warning("Web", "Keep import from the web editor failed: ${it.message}", it)
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("unreadable"))
                    }
                },
            )
        }

        sse("/api/events") {
            val device = call.device.id
            try {
                send(ServerSentEvent(event = "notes", data = webJson.encodeToString(RevisionEvent(env.sync.revision.value))))
                val revoked = env.auth.revoked.filter { it == null || it == device }.map { Outgoing.Close }
                val closing = env.closing.map { Outgoing.Bye }
                val revisions = env.sync.revision.drop(1).map { Outgoing.Event("notes", webJson.encodeToString(RevisionEvent(it))) }
                val labels = env.labels.observeAll().drop(1).map { Outgoing.Event("labels", "{}") }
                val theme = env.theme.distinctUntilChanged().drop(1).map { Outgoing.Event("theme", webJson.encodeToString(it)) }
                val heartbeat = flow {
                    while (true) {
                        delay(HEARTBEAT_MS)
                        emit(Outgoing.Heartbeat)
                    }
                }
                merge(revoked, closing, revisions, labels, theme, heartbeat)
                    .takeWhile { it != Outgoing.Close }
                    .collect { out ->
                        when (out) {
                            is Outgoing.Event -> send(ServerSentEvent(event = out.name, data = out.data))
                            Outgoing.Heartbeat -> send(ServerSentEvent(comments = "ping"))
                            Outgoing.Bye -> send(ServerSentEvent(event = "bye", data = "{}"))
                            Outgoing.Close -> Unit
                        }
                    }
            } finally {
                withContext(NonCancellable) { editing.endAll(device) }
            }
        }

        get("/{path...}") {
            val path = call.parameters.getAll("path").orEmpty().joinToString("/").ifEmpty { "index.html" }
            if (path.startsWith("api/") || path.split('/').any { it == ".." || it.isEmpty() }) {
                return@get call.respond(HttpStatusCode.NotFound)
            }
            val etag = "\"${env.appVersion}\""
            if (call.request.header(HttpHeaders.IfNoneMatch) == etag) return@get call.respond(HttpStatusCode.NotModified)
            val bytes = env.asset(path) ?: return@get call.respond(HttpStatusCode.NotFound)
            call.response.header(HttpHeaders.ETag, etag)
            call.response.header(HttpHeaders.CacheControl, "no-cache")
            call.respondBytes(bytes, contentTypeOf(path))
        }
    }
}

private sealed interface Outgoing {
    data class Event(val name: String, val data: String) : Outgoing
    data object Heartbeat : Outgoing
    data object Bye : Outgoing
    data object Close : Outgoing
}

/**
 * Refuses requests that name another host (DNS rebinding), API calls from computers that are not paired, and
 * changes that come from another site (CSRF).
 */
private fun guard(env: WebEnvironment) = createApplicationPlugin("WebGuard") {
    onCall { call ->
        val host = call.request.header(HttpHeaders.Host)
        val expected = env.expectedHost()
        if (expected != null && !host.equals(expected, ignoreCase = true)) {
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("wrong_host"))
            return@onCall
        }
        if (call.request.httpMethod != HttpMethod.Get && call.request.header(HttpHeaders.Origin) != "http://$host") {
            call.respond(HttpStatusCode.Forbidden, ErrorResponse("wrong_origin"))
            return@onCall
        }
        val path = call.request.path()
        if (!path.startsWith("/api/") || path == "/api/pair") return@onCall
        val device = env.auth.authenticate(call.request.cookies[DEVICE_COOKIE])
        if (device == null) {
            call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unpaired"))
            return@onCall
        }
        call.attributes.put(DeviceKey, device)
    }
}

/**
 * Tracks which notes each computer has open so web edits land in the note history like phone edits, and
 * remembers the text each computer last saw so a change made meanwhile on the phone is reported as a conflict.
 */
private class EditingSessions(private val versions: VersionRepository) {
    private val open = ConcurrentHashMap<Long, MutableSet<Long>>()
    private val seen = ConcurrentHashMap<Pair<Long, Long>, Pair<String, String>>()

    suspend fun ensure(device: Long, note: Note) {
        val notes = open.getOrPut(device) { ConcurrentHashMap.newKeySet() }
        if (open.values.none { note.id in it }) versions.beginSession(note.id, note.title, note.body, note.updatedAt)
        notes += note.id
        seen.putIfAbsent(device to note.id, note.title to note.body)
    }

    fun saw(device: Long, noteId: Long, title: String, body: String) {
        seen[device to noteId] = title to body
    }

    /** The note's text changed after this computer last saw it, and the computer has not caught up since. */
    fun changedElsewhere(device: Long, note: Note, baseUpdatedAt: Long): Boolean {
        if (note.updatedAt == baseUpdatedAt) return false
        val last = seen[device to note.id] ?: return true
        return last != (note.title to note.body)
    }

    fun forget(noteId: Long) {
        open.values.forEach { it -= noteId }
    }

    suspend fun end(device: Long, noteId: Long) {
        open[device]?.remove(noteId)
        seen.remove(device to noteId)
        if (open.values.none { noteId in it }) versions.endSession(noteId)
    }

    suspend fun endAll(device: Long) {
        open[device]?.toList()?.forEach { end(device, it) }
    }
}

private class TooLargeException : Exception("Upload is larger than the limit")

private class LimitedInputStream(private val input: InputStream, private val limit: Long) : InputStream() {
    private var count = 0L

    override fun read(): Int = input.read().also { if (it >= 0) advance(1) }

    override fun read(b: ByteArray, off: Int, len: Int): Int = input.read(b, off, len).also { if (it > 0) advance(it.toLong()) }

    override fun close() = input.close()

    private fun advance(by: Long) {
        count += by
        if (count > limit) throw TooLargeException()
    }
}

private suspend fun WebEnvironment.visibleNote(call: ApplicationCall): Note? =
    call.parameters["id"]?.toLongOrNull()?.let { notes.get(it) }?.takeUnless { it.locked }

private suspend fun WebEnvironment.webLabels(): List<WebLabel> = labels.observeAll().first().map { WebLabel(it.id, it.name) }

private suspend fun ApplicationCall.badRequest() = respond(HttpStatusCode.BadRequest, ErrorResponse("bad_request"))

fun Note.toWeb() = WebNote(
    id = id,
    title = title,
    body = body,
    color = color.name,
    pinned = pinnedToTop,
    state = state.name,
    position = position,
    createdAt = createdAt,
    updatedAt = updatedAt,
    trashedAt = trashedAt,
    labelIds = labelIds.sorted(),
)

private fun contentTypeOf(path: String): ContentType = when (path.substringAfterLast('.', "").lowercase()) {
    "html" -> ContentType.Text.Html.withParameter("charset", "utf-8")
    "js", "mjs" -> ContentType.Text.JavaScript.withParameter("charset", "utf-8")
    "css" -> ContentType.Text.CSS.withParameter("charset", "utf-8")
    "json" -> ContentType.Application.Json
    "svg" -> ContentType.Image.SVG
    "png" -> ContentType.Image.PNG
    "ico" -> ContentType("image", "x-icon")
    "woff2" -> ContentType("font", "woff2")
    "txt" -> ContentType.Text.Plain.withParameter("charset", "utf-8")
    else -> ContentType.Application.OctetStream
}
