package dev.aliarslan.musvedde.data.storage

import com.google.common.truth.Truth.assertThat
import dev.aliarslan.musvedde.data.db.inMemoryDatabase
import dev.aliarslan.musvedde.data.note.NoteRepository
import dev.aliarslan.musvedde.data.note.VersionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StorageRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val db = inMemoryDatabase()
    private var now = 1_000L
    private val notes = NoteRepository(db, clock = { now }, io = Dispatchers.Unconfined)
    private val versions = VersionRepository(db, clock = { now }, io = Dispatchers.Unconfined)

    @After
    fun tearDown() = db.close()

    private fun repository() = StorageRepository(
        db = db,
        databaseFiles = { listOf(tmp.root.resolve("musvedde.db"), tmp.root.resolve("musvedde.db-wal")) },
        safetyBackupDir = tmp.root.resolve("safety"),
        io = Dispatchers.Unconfined,
    )

    @Test
    fun reportSumsNotesVersionsAndFilesOnDisk() = runTest {
        tmp.root.resolve("musvedde.db").writeBytes(ByteArray(4096))
        tmp.root.resolve("musvedde.db-wal").writeBytes(ByteArray(1024))
        tmp.root.resolve("safety").apply { mkdirs() }.resolve("a.zip").writeBytes(ByteArray(300))
        val big = notes.create("Uzun", "ş".repeat(500))
        val small = notes.create("", "kısa")
        versions.beginSession(big, "Uzun", "ş".repeat(500), now)
        now += 10
        notes.updateContent(big, "Uzun", "yeni")
        versions.endSession(big)

        val report = repository().report()

        assertThat(report.databaseBytes).isEqualTo(5120)
        assertThat(report.safetyBackupBytes).isEqualTo(300)
        assertThat(report.totalOnDiskBytes).isEqualTo(5420)
        assertThat(report.noteCount).isEqualTo(2)
        assertThat(report.noteBytes).isEqualTo(("Uzun" + "yeni").toByteArray().size + "kısa".toByteArray().size.toLong())
        assertThat(report.versionCount).isEqualTo(2)
        assertThat(report.versionBytes).isEqualTo(("Uzun" + "ş".repeat(500)).toByteArray().size + ("Uzun" + "yeni").toByteArray().size.toLong())
        assertThat(report.notes.map { it.id }).containsExactly(big, small).inOrder()
        assertThat(report.notes.first().versionCount).isEqualTo(2)
    }
}
