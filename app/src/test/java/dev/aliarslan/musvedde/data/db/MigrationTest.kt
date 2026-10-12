package dev.aliarslan.musvedde.data.db

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MusveddeDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate1To2KeepsNotesAndAddsVersionTables() {
        helper.createDatabase(dbPath, 1).use { db ->
            db.execSQL(
                "INSERT INTO notes (id, title, body, color, pinnedToTop, pinnedToNotification, state, position, createdAt, updatedAt, trashedAt) " +
                    "VALUES (1, 'Gece', 'satır', 'DEFAULT', 0, 0, 'ACTIVE', 1024, 1, 1, NULL)",
            )
        }

        helper.runMigrationsAndValidate(dbPath, 2, true).use { db ->
            db.query("SELECT body FROM notes WHERE id = 1").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.getString(0)).isEqualTo("satır")
            }
            db.query("SELECT COUNT(*) FROM note_versions").use { cursor ->
                cursor.moveToFirst()
                assertThat(cursor.getInt(0)).isEqualTo(0)
            }
        }
    }

    @Test
    fun migrate2To3AddsFoldersAndVisibilityFlags() {
        helper.createDatabase(dbPath, 2).use { db ->
            db.execSQL(
                "INSERT INTO notes (id, title, body, color, pinnedToTop, pinnedToNotification, state, position, createdAt, updatedAt, trashedAt) " +
                    "VALUES (1, 'Gece', 'satır', 'DEFAULT', 0, 0, 'ACTIVE', 1024, 1, 1, NULL)",
            )
        }

        helper.runMigrationsAndValidate(dbPath, 3, true).use { db ->
            db.query("SELECT folderId, hidden, locked FROM notes WHERE id = 1").use { cursor ->
                assertThat(cursor.moveToFirst()).isTrue()
                assertThat(cursor.isNull(0)).isTrue()
                assertThat(cursor.getInt(1)).isEqualTo(0)
                assertThat(cursor.getInt(2)).isEqualTo(0)
            }
            db.query("SELECT COUNT(*) FROM folders").use { cursor ->
                cursor.moveToFirst()
                assertThat(cursor.getInt(0)).isEqualTo(0)
            }
        }
    }

    private val dbPath = ApplicationProvider.getApplicationContext<Context>().getDatabasePath("migration-test.db").absolutePath
}
