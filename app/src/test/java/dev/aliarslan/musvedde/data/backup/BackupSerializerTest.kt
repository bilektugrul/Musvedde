package dev.aliarslan.musvedde.data.backup

import com.google.common.truth.Truth.assertThat
import dev.aliarslan.musvedde.data.db.LabelEntity
import dev.aliarslan.musvedde.data.db.MusveddeDatabase
import dev.aliarslan.musvedde.data.db.NoteEntity
import dev.aliarslan.musvedde.data.db.inMemoryDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class BackupSerializerTest {
    private val source = inMemoryDatabase()
    private val target = inMemoryDatabase()

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    private fun serializer(db: MusveddeDatabase) = BackupSerializer(db, clock = { 1_000L })

    private fun NoteEntity.withoutId() = copy(id = 0)

    @Test
    fun roundTripPreservesNotesAndLabels() = runTest {
        val states = listOf("ACTIVE", "ARCHIVED", "TRASHED")
        val ids = source.noteDao().insertAll(
            (0 until 1_234).map { i ->
                NoteEntity(
                    title = if (i % 3 == 0) "" else "Not $i",
                    body = "satır $i\n\nikinci paragraf \"tırnak\" \\ $i",
                    color = if (i % 2 == 0) "TEAL" else "DEFAULT",
                    pinnedToTop = i % 7 == 0,
                    pinnedToNotification = i % 11 == 0,
                    state = states[i % 3],
                    position = i * 1024L,
                    createdAt = i.toLong(),
                    updatedAt = i + 5L,
                    trashedAt = if (i % 3 == 2) i + 9L else null,
                )
            },
        )
        val love = source.labelDao().insert(LabelEntity(name = "aşk"))
        source.labelDao().insert(LabelEntity(name = "boş"))
        source.labelDao().addToNotes(love, ids.take(10))

        val bytes = ByteArrayOutputStream().also { serializer(source).write(it) }.toByteArray()
        val content = serializer(target).read(ByteArrayInputStream(bytes))
        RestoreService(target, serializer(target)).restore(content, RestoreMode.REPLACE).getOrThrow()

        assertThat(content.manifest.noteCount).isEqualTo(1_234)
        assertThat(content.manifest.formatVersion).isEqualTo(2)
        val restored = target.noteDao().pageAll(2_000, 0).map { it.withoutId() }
        val original = source.noteDao().pageAll(2_000, 0).map { it.withoutId() }
        assertThat(restored).containsExactlyElementsIn(original)
        assertThat(target.labelDao().all().map { it.name }).containsExactly("aşk", "boş")
        assertThat(target.labelDao().allCrossRefs()).hasSize(10)
    }

    @Test
    fun restoreRejectsUnknownFormatVersion() {
        val bytes = zipOf(
            "manifest.json" to """{"formatVersion":99,"appVersion":"9.0","createdAt":0,"noteCount":0}""",
            "data.json" to """{"labels":[],"notes":[]}""",
        )

        assertThrows(BackupFormatException::class.java) {
            serializer(target).read(ByteArrayInputStream(bytes))
        }
    }

    @Test
    fun missingManifestIsRejected() {
        val bytes = zipOf("data.json" to """{"labels":[],"notes":[]}""")

        assertThrows(BackupFormatException::class.java) {
            serializer(target).read(ByteArrayInputStream(bytes))
        }
    }

    @Test
    fun fileNameFormat() {
        val now = LocalDateTime.of(2026, 10, 11, 9, 5, 7).toInstant(ZoneOffset.UTC).toEpochMilli()

        assertThat(BackupSerializer.fileName(now, ZoneOffset.UTC)).isEqualTo("musvedde-backup-20261011-090507.zip")
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
