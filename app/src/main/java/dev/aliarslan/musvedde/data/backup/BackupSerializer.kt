package dev.aliarslan.musvedde.data.backup

import dev.aliarslan.musvedde.BuildConfig
import dev.aliarslan.musvedde.core.Clock
import dev.aliarslan.musvedde.data.db.MusveddeDatabase
import dev.aliarslan.musvedde.data.db.NoteEntity
import dev.aliarslan.musvedde.data.lock.Aead
import dev.aliarslan.musvedde.data.lock.BackupKey
import dev.aliarslan.musvedde.domain.Settings
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.util.Base64
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject

fun interface VaultKeySource {
    suspend fun backupKey(): BackupKey?
}

class BackupSerializer @Inject constructor(
    private val db: MusveddeDatabase,
    private val clock: Clock,
    private val vaultKeys: VaultKeySource = VaultKeySource { null },
    private val settingsStore: BackupSettingsStore = BackupSettingsStore.NONE,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Writes every note; those in the secret folder only go into the encrypted vault. */
    suspend fun write(out: OutputStream) {
        val labels = db.labelDao().all()
        val labelNames = labels.associate { it.id to it.name }
        val noteLabels = db.labelDao().allCrossRefs().groupBy({ it.noteId }, { labelNames.getValue(it.labelId) })
        val lockedNotes = mutableListOf<BackupNote>()

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
            val versions = db.noteVersionDao().forNotes(page.map { it.id }).groupBy { it.noteId }
            page.forEach { note ->
                val history = versions[note.id].orEmpty().map { BackupVersion(it.title, it.body, it.createdAt) }
                val backup = note.toBackup(noteLabels[note.id].orEmpty(), history)
                if (note.locked) {
                    lockedNotes += backup
                } else {
                    if (count > 0) writer.write(",")
                    writer.write(json.encodeToString(backup))
                    count++
                }
            }
            lastId = page.last().id
        }
        writer.write("]}")
        writer.flush()
        zip.closeEntry()

        if (lockedNotes.isNotEmpty()) {
            val key = vaultKeys.backupKey() ?: throw BackupKeyUnavailableException()
            val sealed = Aead.encrypt(key.key, json.encodeToString(lockedNotes).toByteArray(Charsets.UTF_8))
            val vault = BackupVault(key.salt.base64(), key.iterations, sealed.base64())
            zip.putNextEntry(ZipEntry(VAULT))
            zip.write(json.encodeToString(vault).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        settingsStore.current()?.let { settings ->
            zip.putNextEntry(ZipEntry(SETTINGS))
            zip.write(json.encodeToString(settings).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }

        zip.putNextEntry(ZipEntry(MANIFEST))
        val manifest = BackupManifest(
            appVersion = BuildConfig.VERSION_NAME,
            createdAt = clock.now(),
            noteCount = count + lockedNotes.size,
            lockedNoteCount = lockedNotes.size,
        )
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
            val vault = entries[VAULT]?.let { json.decodeFromString<BackupVault>(it) }
            val settings = entries[SETTINGS]?.let { runCatching { json.decodeFromString<Settings>(it).sanitized() }.getOrNull() }
            return BackupContent(manifest, data.labels, data.notes, vault, settings)
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
                if (entry.name in setOf(MANIFEST, DATA, VAULT, SETTINGS)) {
                    result[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
                }
            }
        }
        return result
    }

    /** Decrypts the vault with a key derived from the PIN; throws [WrongBackupPinException] for a wrong PIN. */
    fun openVault(vault: BackupVault, key: ByteArray): List<BackupNote> {
        val plain = try {
            Aead.decrypt(key, vault.data.unbase64())
        } catch (e: GeneralSecurityException) {
            throw WrongBackupPinException()
        }
        return json.decodeFromString(plain.toString(Charsets.UTF_8))
    }

    private fun ByteArray.base64(): String = Base64.getEncoder().encodeToString(this)

    private fun NoteEntity.toBackup(labels: List<String>, versions: List<BackupVersion>) = BackupNote(
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
        versions = versions,
        locked = locked,
    )

    companion object {
        const val FORMAT_VERSION = 3
        const val FILE_PREFIX = "musvedde-backup-"
        private const val MANIFEST = "manifest.json"
        private const val DATA = "data.json"
        private const val VAULT = "vault.json"
        private const val SETTINGS = "settings.json"
        private const val PAGE_SIZE = 500
        private val FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

        fun fileName(now: Long, zone: ZoneId = ZoneId.systemDefault()): String =
            FILE_PREFIX + FILE_TIME.format(Instant.ofEpochMilli(now).atZone(zone)) + ".zip"
    }
}

internal fun String.unbase64(): ByteArray = Base64.getDecoder().decode(this)
