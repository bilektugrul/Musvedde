package dev.aliarslan.musvedde.data.backup

import com.google.common.truth.Truth.assertThat
import dev.aliarslan.musvedde.data.db.inMemoryDatabase
import dev.aliarslan.musvedde.data.note.NoteRepository
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
}
