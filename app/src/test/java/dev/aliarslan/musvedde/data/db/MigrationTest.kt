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
    fun migrate4To5BringsHiddenNotesBack() {
        helper.createDatabase(dbPath, 4).use { db ->
            db.execSQL(
                "INSERT INTO notes (id, title, body, color, pinnedToTop, pinnedToNotification, state, position, createdAt, updatedAt, trashedAt, hidden, locked) " +
                    "VALUES (1, 'gizli', 'deniz', 'DEFAULT', 0, 0, 'ACTIVE', 1, 1, 1, NULL, 1, 0), " +
                    "(2, 'kasa', 'deniz', 'DEFAULT', 0, 0, 'ACTIVE', 2, 1, 1, NULL, 0, 1)",
            )
        }

        helper.runMigrationsAndValidate(dbPath, 5, true, MIGRATION_4_5).use { db ->
            db.query("SELECT id, locked FROM notes ORDER BY id").use { cursor ->
                val rows = buildList { while (cursor.moveToNext()) add(cursor.getLong(0) to cursor.getInt(1)) }
                assertThat(rows).containsExactly(1L to 0, 2L to 1).inOrder()
            }
            db.query("SELECT rowid FROM notes_fts WHERE notes_fts MATCH 'deniz'").use { cursor ->
                assertThat(cursor.count).isEqualTo(2)
            }
        }
    }

    @Test
    fun migrate3To4MovesLockedFoldersIntoTheSecretFolder() {
        helper.createDatabase(dbPath, 3).use { db ->
            db.execSQL("INSERT INTO folders (id, name, locked) VALUES (1, 'Kasa', 1), (2, 'Açık', 0)")
            listOf(1 to 1, 2 to 2, 3 to null).forEach { (id, folder) ->
                db.execSQL(
                    "INSERT INTO notes (id, title, body, color, pinnedToTop, pinnedToNotification, state, position, createdAt, updatedAt, trashedAt, folderId, hidden, locked) " +
                        "VALUES ($id, 'not $id', 'deniz $id', 'DEFAULT', 0, 0, 'ACTIVE', $id, 1, 1, NULL, $folder, 0, 0)",
                )
            }
        }

        helper.runMigrationsAndValidate(dbPath, 4, true, MIGRATION_3_4).use { db ->
            db.query("SELECT id, locked FROM notes ORDER BY id").use { cursor ->
                val rows = buildList { while (cursor.moveToNext()) add(cursor.getLong(0) to cursor.getInt(1)) }
                assertThat(rows).containsExactly(1L to 1, 2L to 0, 3L to 0).inOrder()
            }
            db.query("SELECT rowid FROM notes_fts WHERE notes_fts MATCH 'deniz'").use { cursor ->
                assertThat(cursor.count).isEqualTo(3)
            }
            db.execSQL("UPDATE notes SET body = 'yağmur' WHERE id = 2")
            db.query("SELECT rowid FROM notes_fts WHERE notes_fts MATCH 'yağmur'").use { cursor ->
                assertThat(cursor.count).isEqualTo(1)
            }
        }
    }

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
