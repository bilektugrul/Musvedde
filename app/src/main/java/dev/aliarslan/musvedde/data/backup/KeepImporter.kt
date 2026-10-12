package dev.aliarslan.musvedde.data.backup

import dev.aliarslan.musvedde.core.Clock
import dev.aliarslan.musvedde.data.note.Positions
import dev.aliarslan.musvedde.domain.NoteColor
import dev.aliarslan.musvedde.domain.NoteState
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject

/** Reads a Google Takeout export of Google Keep and turns its notes into backup content that can be merged in. */
class KeepImporter @Inject constructor(private val clock: Clock) {
    private val json = Json { ignoreUnknownKeys = true }

    fun read(input: InputStream): BackupContent {
        val keepNotes = buildList {
            val zip = ZipInputStream(input)
            while (true) {
                val entry = zip.nextEntry ?: break
                if (!entry.isDirectory && entry.name.endsWith(".json", ignoreCase = true)) {
                    val text = zip.readBytes().toString(Charsets.UTF_8)
                    runCatching { json.decodeFromString<KeepNote>(text) }.getOrNull()
                        ?.takeIf { it.userEditedTimestampUsec > 0 }
                        ?.let(::add)
                }
            }
        }
        if (keepNotes.isEmpty()) throw BackupFormatException("No Google Keep notes found in this file")

        val notes = keepNotes
            .filter { !it.isTrashed && (it.title.isNotBlank() || it.body().isNotBlank()) }
            .sortedBy { it.userEditedTimestampUsec }
            .mapIndexed { index, note ->
                val updatedAt = note.userEditedTimestampUsec / 1000
                BackupNote(
                    title = note.title,
                    body = note.body(),
                    color = note.noteColor().name,
                    pinnedToTop = note.isPinned,
                    pinnedToNotification = false,
                    state = (if (note.isArchived) NoteState.ARCHIVED else NoteState.ACTIVE).name,
                    position = index * Positions.STEP,
                    createdAt = note.createdTimestampUsec.takeIf { it > 0 }?.div(1000) ?: updatedAt,
                    updatedAt = updatedAt,
                    labelNames = note.labels.map { it.name }.filter { it.isNotBlank() }.distinct(),
                )
            }
        return BackupContent(
            manifest = BackupManifest(appVersion = "Google Keep", createdAt = clock.now(), noteCount = notes.size),
            labels = notes.flatMap { it.labelNames }.distinct().map { BackupLabel(it) },
            notes = notes,
        )
    }

    private fun KeepNote.body(): String =
        listContent?.joinToString("\n") { (if (it.isChecked) "☑ " else "☐ ") + it.text } ?: textContent

    private fun KeepNote.noteColor(): NoteColor =
        if (color == "CERULEAN") NoteColor.DARK_BLUE else NoteColor.fromName(color)

    @Serializable
    private data class KeepNote(
        val title: String = "",
        val textContent: String = "",
        val listContent: List<KeepListItem>? = null,
        val color: String = "DEFAULT",
        val isPinned: Boolean = false,
        val isArchived: Boolean = false,
        val isTrashed: Boolean = false,
        val createdTimestampUsec: Long = 0,
        val userEditedTimestampUsec: Long = 0,
        val labels: List<KeepLabel> = emptyList(),
    )

    @Serializable
    private data class KeepListItem(val text: String = "", val isChecked: Boolean = false)

    @Serializable
    private data class KeepLabel(val name: String = "")
}
