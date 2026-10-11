package dev.aliarslan.musvedde.data.backup

import dev.aliarslan.musvedde.BuildConfig
import dev.aliarslan.musvedde.core.Clock
import dev.aliarslan.musvedde.data.db.MusveddeDatabase
import dev.aliarslan.musvedde.data.db.NoteEntity
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject

class BackupSerializer @Inject constructor(
    private val db: MusveddeDatabase,
    private val clock: Clock,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun write(out: OutputStream) {
        val labels = db.labelDao().all()
        val labelNames = labels.associate { it.id to it.name }
        val noteLabels = db.labelDao().allCrossRefs().groupBy({ it.noteId }, { labelNames.getValue(it.labelId) })

        val zip = ZipOutputStream(out)
        zip.putNextEntry(ZipEntry(DATA))
        val writer = zip.bufferedWriter(Charsets.UTF_8)
        writer.write("{\"labels\":")
        writer.write(json.encodeToString(labels.map { BackupLabel(it.name) }))
        writer.write(",\"notes\":[")
        var count = 0
        var lastId = 0L
        while (true) {
            val page = db.noteDao().pageAfter(lastId, PAGE_SIZE)
            if (page.isEmpty()) break
            page.forEach { note ->
                if (count > 0) writer.write(",")
                writer.write(json.encodeToString(note.toBackup(noteLabels[note.id].orEmpty())))
                count++
            }
            lastId = page.last().id
        }
        writer.write("]}")
        writer.flush()
        zip.closeEntry()

        zip.putNextEntry(ZipEntry(MANIFEST))
        val manifest = BackupManifest(appVersion = BuildConfig.VERSION_NAME, createdAt = clock.now(), noteCount = count)
        zip.write(json.encodeToString(manifest).toByteArray(Charsets.UTF_8))
        zip.closeEntry()
        zip.finish()
        zip.flush()
    }

    fun read(input: InputStream): BackupContent {
        val entries = try {
            readEntries(input)
        } catch (e: IOException) {
            throw BackupFormatException("Unreadable backup archive", e)
        }
        val manifestText = entries[MANIFEST] ?: throw BackupFormatException("Missing $MANIFEST")
        val dataText = entries[DATA] ?: throw BackupFormatException("Missing $DATA")
        try {
            val manifest = json.decodeFromString<BackupManifest>(manifestText)
            if (manifest.formatVersion > FORMAT_VERSION || manifest.formatVersion < 1) {
                throw BackupFormatException("Unsupported backup format version ${manifest.formatVersion}")
            }
            val data = json.decodeFromString<BackupData>(dataText)
            return BackupContent(manifest, data.labels, data.notes)
        } catch (e: SerializationException) {
            throw BackupFormatException("Corrupt backup data", e)
        } catch (e: IllegalArgumentException) {
            throw BackupFormatException("Corrupt backup data", e)
        }
    }

    private fun readEntries(input: InputStream): Map<String, String> {
        val result = mutableMapOf<String, String>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == MANIFEST || entry.name == DATA) {
                    result[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                }
            }
        }
        return result
    }

    private fun NoteEntity.toBackup(labels: List<String>) = BackupNote(
        title = title,
        body = body,
        color = color,
        pinnedToTop = pinnedToTop,
        pinnedToNotification = pinnedToNotification,
        state = state,
        position = position,
        createdAt = createdAt,
        updatedAt = updatedAt,
        trashedAt = trashedAt,
        labelNames = labels,
    )

    companion object {
        const val FORMAT_VERSION = 1
        const val FILE_PREFIX = "musvedde-backup-"
        private const val MANIFEST = "manifest.json"
        private const val DATA = "data.json"
        private const val PAGE_SIZE = 500
        private val FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

        fun fileName(now: Long, zone: ZoneId = ZoneId.systemDefault()): String =
            FILE_PREFIX + FILE_TIME.format(Instant.ofEpochMilli(now).atZone(zone)) + ".zip"
    }
}
