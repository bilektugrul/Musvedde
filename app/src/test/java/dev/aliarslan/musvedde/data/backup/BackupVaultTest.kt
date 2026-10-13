package dev.aliarslan.musvedde.data.backup

import com.google.common.truth.Truth.assertThat
import dev.aliarslan.musvedde.data.db.inMemoryDatabase
import dev.aliarslan.musvedde.data.lock.FakeKeyWrapper
import dev.aliarslan.musvedde.data.lock.LockConfig
import dev.aliarslan.musvedde.data.lock.LockManager
import dev.aliarslan.musvedde.data.note.NoteFilter
import dev.aliarslan.musvedde.data.note.NoteRepository
import dev.aliarslan.musvedde.data.settings.InMemoryPreferencesDataStore
import dev.aliarslan.musvedde.domain.NoteState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
class BackupVaultTest {
    private val source = inMemoryDatabase()
    private val target = inMemoryDatabase()
    private val lock = LockManager(InMemoryPreferencesDataStore(), FakeKeyWrapper(), clock = { 1L }, LockConfig(1_000, 2_000))
    private val notes = NoteRepository(source, clock = { 1L }, io = Dispatchers.Unconfined)
    private val targetNotes = NoteRepository(target, clock = { 1L }, io = Dispatchers.Unconfined)

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    private suspend fun backup(): ByteArray =
        ByteArrayOutputStream().also { BackupSerializer(source, clock = { 1L }, vaultKeys = lock).write(it) }.toByteArray()

    private fun entries(bytes: ByteArray): Map<String, String> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                put(entry.name, zip.readBytes().toString(Charsets.UTF_8))
            }
        }
    }

    private suspend fun lockedSetup(): Pair<Long, Long> {
        lock.setPin("2468")
        val inVault = notes.create("kasa notu", "kasadaki sır", secret = true)
        val lockedNote = notes.create("tek kilit", "tek başına sır")
        notes.setLocked(listOf(lockedNote), true)
        notes.create("açık", "herkes okur")
        return inVault to lockedNote
    }

    @Test
    fun lockedContentIsOnlyInTheEncryptedVault() = runTest {
        lockedSetup()

        val files = entries(backup())

        assertThat(files["data.json"]).contains("herkes okur")
        assertThat(files["data.json"]).doesNotContain("sır")
        assertThat(files["vault.json"]).doesNotContain("sır")
        assertThat(files.values.joinToString()).doesNotContain("2468")
        assertThat(files["manifest.json"]).contains("\"lockedNoteCount\":2")
    }

    @Test
    fun restoringNeedsTheRightPin() = runTest {
        lockedSetup()
        val content = BackupSerializer(target, clock = { 1L }).read(ByteArrayInputStream(backup()))
        val restore = RestoreService(target, BackupSerializer(target, clock = { 1L }))

        assertThat(restore.restore(content, RestoreMode.MERGE).exceptionOrNull()).isInstanceOf(BackupPinRequiredException::class.java)
        assertThat(restore.restore(content, RestoreMode.MERGE, pin = "1111").exceptionOrNull()).isInstanceOf(WrongBackupPinException::class.java)
        assertThat(target.noteDao().count()).isEqualTo(0)

        assertThat(restore.restore(content, RestoreMode.MERGE, pin = "2468").getOrThrow()).isEqualTo(3)
    }

    @Test
    fun restoredSecretNotesGoBackIntoTheSecretFolder() = runTest {
        lockedSetup()
        val content = BackupSerializer(target, clock = { 1L }).read(ByteArrayInputStream(backup()))
        RestoreService(target, BackupSerializer(target, clock = { 1L })).restore(content, RestoreMode.REPLACE, pin = "2468").getOrThrow()

        val main = targetNotes.observe(NoteFilter.InState(NoteState.ACTIVE)).first()
        assertThat(main.map { it.title }).containsExactly("açık")
        val secret = targetNotes.observe(NoteFilter.Secret).first()
        assertThat(secret.map { it.title }).containsExactly("kasa notu", "tek kilit")
    }

    @Test
    fun backupsWithoutLockedNotesHaveNoVault() = runTest {
        notes.create("açık", "herkes okur")

        val bytes = backup()

        assertThat(entries(bytes)).doesNotContainKey("vault.json")
        val content = BackupSerializer(target, clock = { 1L }).read(ByteArrayInputStream(bytes))
        assertThat(RestoreService(target, BackupSerializer(target, clock = { 1L })).restore(content, RestoreMode.MERGE).getOrThrow()).isEqualTo(1)
    }

    @Test
    fun lockedNotesAreNeverWrittenWithoutAKey() = runTest {
        lockedSetup()
        val noKeys = BackupSerializer(source, clock = { 1L }, vaultKeys = { null })

        assertThrows(BackupKeyUnavailableException::class.java) {
            kotlinx.coroutines.runBlocking { noKeys.write(ByteArrayOutputStream()) }
        }
    }
}
