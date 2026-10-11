package dev.aliarslan.musvedde.data.backup

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.aliarslan.musvedde.data.settings.InMemoryPreferencesDataStore
import dev.aliarslan.musvedde.data.settings.SettingsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FolderBackupTargetTest {
    private val settings = SettingsRepository(InMemoryPreferencesDataStore())
    private val target = FolderBackupTarget(ApplicationProvider.getApplicationContext(), settings)

    @Test
    fun notReadyWithoutFolder() = runTest {
        assertThat(target.isReady()).isFalse()
    }

    @Test
    fun folderTargetReportsMissingPermission() = runTest {
        settings.update {
            it.copy(backupFolderUri = "content://com.android.externalstorage.documents/tree/primary%3AMusvedde")
        }

        assertThat(target.isReady()).isFalse()
        assertThrows(BackupTargetUnavailable::class.java) {
            kotlinx.coroutines.runBlocking { target.write("x.zip") { it.write(1) } }
        }
    }
}
