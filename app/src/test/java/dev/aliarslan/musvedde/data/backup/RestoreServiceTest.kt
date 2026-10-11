package dev.aliarslan.musvedde.data.backup

import com.google.common.truth.Truth.assertThat
import dev.aliarslan.musvedde.data.db.inMemoryDatabase
import dev.aliarslan.musvedde.data.note.NoteRepository
import dev.aliarslan.musvedde.data.note.VersionRepository
import kotlinx.coroutines.flow.first
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import dev.aliarslan.musvedde.domain.NoteColor
import dev.aliarslan.musvedde.domain.NoteState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
class RestoreServiceTest {
    private val db = inMemoryDatabase()
    private var now = 10L
    private val notes = NoteRepository(db, clock = { now++ }, io = Dispatchers.Unconfined)
    private val serializer = BackupSerializer(db, clock = { 1L })
    private val restore = RestoreService(db, serializer)

    @After
    fun tearDown() = db.close()

    private suspend fun backup(): ByteArray = ByteArrayOutputStream().also { serializer.write(it) }.toByteArray()

    @Test
    fun mergeSkipsDuplicates() = runTest {
        notes.create("", "aynı")
        val bytes = backup()
        notes.create("", "yeni")

        val added = restore.restoreFrom(ByteArrayInputStream(bytes), RestoreMode.MERGE).getOrThrow()

        assertThat(added).isEqualTo(0)
        assertThat(db.noteDao().count()).isEqualTo(2)
    }

    @Test
    fun mergeAddsMissingNotesAboveExisting() = runTest {
        val old = notes.create("", "eski")
        val bytes = backup()
        notes.deleteForever(listOf(old))
        val current = notes.create("", "şimdiki")

        val added = restore.restoreFrom(ByteArrayInputStream(bytes), RestoreMode.MERGE).getOrThrow()

        assertThat(added).isEqualTo(1)
        val all = db.noteDao().pageAll(10, 0)
        val restored = all.single { it.body == "eski" }
        assertThat(restored.position).isGreaterThan(all.single { it.id == current }.position)
    }

    @Test
    fun replaceWipesExisting() = runTest {
        notes.create("", "yedekteki")
        val bytes = backup()
        notes.create("", "sonradan")

        restore.restoreFrom(ByteArrayInputStream(bytes), RestoreMode.REPLACE).getOrThrow()

        assertThat(db.noteDao().pageAll(10, 0).map { it.body }).containsExactly("yedekteki")
    }

    @Test
    fun versionHistoryIsBackedUpAndRestored() = runTest {
        val versions = VersionRepository(db, clock = { now }, io = Dispatchers.Unconfined)
        val id = notes.create("", "ilk")
        versions.beginSession(id, "", "ilk", 5)
        notes.updateContent(id, "", "ikinci")
        versions.endSession(id)
        val bytes = backup()

        restore.restoreFrom(ByteArrayInputStream(bytes), RestoreMode.REPLACE).getOrThrow()

        val restoredId = db.noteDao().pageAll(1, 0).single().id
        val restored = versions.observe(restoredId).first()
        assertThat(restored.map { it.body }).containsExactly("ikinci", "ilk").inOrder()
        assertThat(restored.last().createdAt).isEqualTo(5)
        assertThat(restored.first().sizeBytes).isEqualTo("ikinci".toByteArray().size.toLong())
    }

    @Test
    fun formatVersionOneBackupsStillRestore() = runTest {
        val bytes = zipOf(
            "manifest.json" to """{"formatVersion":1,"appVersion":"0.1.0","createdAt":0,"noteCount":1}""",
            "data.json" to """{"labels":[],"notes":[{"title":"","body":"eski yedek","color":"DEFAULT","pinnedToTop":false,"pinnedToNotification":false,"state":"ACTIVE","position":0,"createdAt":1,"updatedAt":1}]}""",
        )

        restore.restoreFrom(ByteArrayInputStream(bytes), RestoreMode.REPLACE).getOrThrow()

        assertThat(db.noteDao().pageAll(1, 0).single().body).isEqualTo("eski yedek")
    }

    @Test
    fun unknownStateIsRestoredAsActive() = runTest {
        val content = BackupContent(
            manifest = BackupManifest(appVersion = "9", createdAt = 0, noteCount = 1),
            labels = emptyList(),
            notes = listOf(
                BackupNote(
                    title = "",
                    body = "garip",
                    color = "NOPE",
                    pinnedToTop = false,
                    pinnedToNotification = false,
                    state = "BOGUS",
                    position = 0,
                    createdAt = 1,
                    updatedAt = 1,
                    trashedAt = 5,
                ),
            ),
        )

        restore.restore(content, RestoreMode.REPLACE).getOrThrow()

        val note = notes.get(db.noteDao().pageAll(1, 0).single().id)!!
        assertThat(note.state).isEqualTo(NoteState.ACTIVE)
        assertThat(note.trashedAt).isNull()
        assertThat(note.color).isEqualTo(NoteColor.DEFAULT)
    }

    @Test
    fun restoreOfCorruptZipChangesNothing() = runTest {
        notes.create("", "a")
        val bytes = backup()
        notes.create("", "b")

        val result = restore.restoreFrom(ByteArrayInputStream(bytes.copyOf(bytes.size / 2)), RestoreMode.REPLACE)

        assertThat(result.isFailure).isTrue()
        assertThat(db.noteDao().count()).isEqualTo(2)
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
}
