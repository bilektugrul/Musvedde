package dev.aliarslan.musvedde.data.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class KeepImporterTest {
    private val importer = KeepImporter(clock = { 42L })

    private fun zip(vararg entries: Pair<String, String>): ByteArrayInputStream {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return ByteArrayInputStream(out.toByteArray())
    }

    @Test
    fun convertsKeepNotes() {
        val content = importer.read(
            zip(
                "Takeout/Keep/Alışveriş.json" to """
                    {"color":"CERULEAN","isTrashed":false,"isPinned":true,"isArchived":false,
                     "title":"Alışveriş","userEditedTimestampUsec":2000000,"createdTimestampUsec":1000000,
                     "labels":[{"name":"Ev"}],
                     "listContent":[{"text":"süt","isChecked":false},{"text":"ekmek","isChecked":true}]}
                """,
                "Takeout/Keep/Fikir.json" to """
                    {"color":"YELLOW","isTrashed":false,"isPinned":false,"isArchived":true,
                     "title":"","textContent":"satır bir\nsatır iki","userEditedTimestampUsec":3000000}
                """,
                "Takeout/Keep/Fikir.html" to "<html></html>",
                "Takeout/Keep/Labels.txt" to "Ev",
            ),
        )

        assertThat(content.notes).hasSize(2)
        val list = content.notes.single { it.title == "Alışveriş" }
        assertThat(list.body).isEqualTo("☐ süt\n☑ ekmek")
        assertThat(list.color).isEqualTo("DARK_BLUE")
        assertThat(list.pinnedToTop).isTrue()
        assertThat(list.createdAt).isEqualTo(1000L)
        assertThat(list.updatedAt).isEqualTo(2000L)
        assertThat(list.labelNames).containsExactly("Ev")
        assertThat(list.state).isEqualTo("ACTIVE")

        val idea = content.notes.single { it.title.isEmpty() }
        assertThat(idea.body).isEqualTo("satır bir\nsatır iki")
        assertThat(idea.state).isEqualTo("ARCHIVED")
        assertThat(idea.createdAt).isEqualTo(3000L)
        assertThat(idea.position).isGreaterThan(list.position)
        assertThat(content.labels.map { it.name }).containsExactly("Ev")
    }

    @Test
    fun skipsTrashedAndEmptyNotes() {
        val content = importer.read(
            zip(
                "Keep/a.json" to """{"isTrashed":true,"title":"silinmiş","textContent":"x","userEditedTimestampUsec":1}""",
                "Keep/b.json" to """{"isTrashed":false,"title":"","textContent":"","userEditedTimestampUsec":1}""",
                "Keep/c.json" to """{"title":"kalan","textContent":"y","userEditedTimestampUsec":5000}""",
            ),
        )

        assertThat(content.notes.map { it.title }).containsExactly("kalan")
    }

    @Test
    fun rejectsArchivesWithoutKeepNotes() {
        assertThrows(BackupFormatException::class.java) {
            importer.read(zip("Takeout/Drive/file.txt" to "nope", "other.json" to """{"foo":1}"""))
        }
    }
}
