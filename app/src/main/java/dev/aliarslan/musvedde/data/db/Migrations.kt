package dev.aliarslan.musvedde.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Drops folders for the single secret folder. Notes from locked folders move into the secret folder so nothing
 * that was private becomes visible. SQLite on older Android versions cannot drop a column, so the notes table
 * is rebuilt; ids stay the same, which keeps the full-text index valid, and its sync triggers are recreated.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("UPDATE notes SET locked = 1 WHERE folderId IN (SELECT id FROM folders WHERE locked = 1)")
        db.execSQL("DROP TABLE folders")
        FTS_TRIGGERS.forEach { db.execSQL("DROP TRIGGER IF EXISTS $it") }
        db.execSQL(
            "CREATE TABLE notes_new (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                "`body` TEXT NOT NULL, `color` TEXT NOT NULL, `pinnedToTop` INTEGER NOT NULL, " +
                "`pinnedToNotification` INTEGER NOT NULL, `state` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `trashedAt` INTEGER, " +
                "`hidden` INTEGER NOT NULL DEFAULT 0, `locked` INTEGER NOT NULL DEFAULT 0)",
        )
        val columns = "id, title, body, color, pinnedToTop, pinnedToNotification, state, position, createdAt, updatedAt, trashedAt, hidden, locked"
        db.execSQL("INSERT INTO notes_new ($columns) SELECT $columns FROM notes")
        db.execSQL("DROP TABLE notes")
        db.execSQL("ALTER TABLE notes_new RENAME TO notes")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_state_pinnedToTop_position` ON `notes` (`state`, `pinnedToTop`, `position`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_updatedAt` ON `notes` (`updatedAt`)")
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_BEFORE_UPDATE BEFORE UPDATE ON `notes` " +
                "BEGIN DELETE FROM `notes_fts` WHERE `docid`=OLD.`rowid`; END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_BEFORE_DELETE BEFORE DELETE ON `notes` " +
                "BEGIN DELETE FROM `notes_fts` WHERE `docid`=OLD.`rowid`; END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_AFTER_UPDATE AFTER UPDATE ON `notes` " +
                "BEGIN INSERT INTO `notes_fts`(`docid`, `title`, `body`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`body`); END",
        )
        db.execSQL(
            "CREATE TRIGGER IF NOT EXISTS room_fts_content_sync_notes_fts_AFTER_INSERT AFTER INSERT ON `notes` " +
                "BEGIN INSERT INTO `notes_fts`(`docid`, `title`, `body`) VALUES (NEW.`rowid`, NEW.`title`, NEW.`body`); END",
        )
    }
}

private val FTS_TRIGGERS = listOf("BEFORE_UPDATE", "BEFORE_DELETE", "AFTER_UPDATE", "AFTER_INSERT")
    .map { "room_fts_content_sync_notes_fts_$it" }
