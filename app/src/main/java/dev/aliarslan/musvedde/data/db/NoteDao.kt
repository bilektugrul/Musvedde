package dev.aliarslan.musvedde.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class NoteContentKey(val title: String, val body: String, val createdAt: Long)

private const val SUMMARY_COLUMNS = """
    n.id, n.title, substr(n.body, 1, 400) AS preview, n.color, n.pinnedToTop, n.pinnedToNotification,
    n.position, n.updatedAt,
    (SELECT GROUP_CONCAT(nl.labelId) FROM note_labels nl WHERE nl.noteId = n.id) AS labelIds
"""

@Dao
interface NoteDao {

    @Query("SELECT $SUMMARY_COLUMNS FROM notes n WHERE n.state = :state ORDER BY n.pinnedToTop DESC, n.position DESC")
    fun observeSummaries(state: String): Flow<List<NoteSummaryRow>>

    @Query(
        """
        SELECT $SUMMARY_COLUMNS FROM notes n
        JOIN note_labels l ON l.noteId = n.id
        WHERE l.labelId = :labelId AND n.state IN ('ACTIVE', 'ARCHIVED')
        ORDER BY n.pinnedToTop DESC, n.position DESC
        """,
    )
    fun observeSummariesByLabel(labelId: Long): Flow<List<NoteSummaryRow>>

    @Query(
        """
        SELECT $SUMMARY_COLUMNS FROM notes n
        JOIN notes_fts ON notes_fts.rowid = n.id
        WHERE notes_fts MATCH :ftsQuery AND n.state != 'TRASHED'
        ORDER BY n.pinnedToTop DESC, n.updatedAt DESC
        """,
    )
    fun searchSummaries(ftsQuery: String): Flow<List<NoteSummaryRow>>

    @Query("SELECT * FROM notes WHERE pinnedToNotification = 1 AND state = 'ACTIVE'")
    fun observePinnedToNotification(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    @Insert
    suspend fun insert(note: NoteEntity): Long

    @Insert
    suspend fun insertAll(notes: List<NoteEntity>): List<Long>

    @Query("UPDATE notes SET title = :title, body = :body, updatedAt = :updatedAt WHERE id = :id AND (title != :title OR body != :body)")
    suspend fun updateContent(id: Long, title: String, body: String, updatedAt: Long): Int

    @Query("UPDATE notes SET color = :color, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun setColor(ids: List<Long>, color: String, updatedAt: Long)

    @Query("UPDATE notes SET pinnedToTop = :pinned, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun setPinnedToTop(ids: List<Long>, pinned: Boolean, updatedAt: Long)

    @Query("UPDATE notes SET pinnedToNotification = :pinned WHERE id = :id")
    suspend fun setPinnedToNotification(id: Long, pinned: Boolean)

    @Query(
        """
        UPDATE notes SET state = :state, trashedAt = :trashedAt, updatedAt = :updatedAt,
            pinnedToNotification = CASE WHEN :state = 'ACTIVE' THEN pinnedToNotification ELSE 0 END
        WHERE id IN (:ids)
        """,
    )
    suspend fun setState(ids: List<Long>, state: String, trashedAt: Long?, updatedAt: Long)

    @Query("UPDATE notes SET position = :position WHERE id = :id")
    suspend fun setPosition(id: Long, position: Long)

    @Query("SELECT id FROM notes ORDER BY position ASC, id ASC")
    suspend fun idsByPositionAscending(): List<Long>

    @Query("SELECT MAX(position) FROM notes")
    suspend fun maxPosition(): Long?

    @Query("DELETE FROM notes WHERE id = :id AND trim(title, ' ' || char(9, 10, 13)) = '' AND trim(body, ' ' || char(9, 10, 13)) = ''")
    suspend fun deleteIfBlank(id: Long)

    @Query("DELETE FROM notes WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("DELETE FROM notes WHERE state = 'TRASHED'")
    suspend fun deleteAllTrashed()

    @Query("DELETE FROM notes WHERE state = 'TRASHED' AND trashedAt < :cutoff")
    suspend fun deleteTrashedBefore(cutoff: Long): Int

    @Query("SELECT COUNT(*) FROM notes WHERE updatedAt > :since")
    suspend fun countChangedSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM notes")
    suspend fun count(): Int

    @Query("SELECT * FROM notes ORDER BY id LIMIT :limit OFFSET :offset")
    suspend fun pageAll(limit: Int, offset: Int): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id > :afterId ORDER BY id LIMIT :limit")
    suspend fun pageAfter(afterId: Long, limit: Int): List<NoteEntity>

    @Query("SELECT title, body, createdAt FROM notes")
    suspend fun contentKeys(): List<NoteContentKey>

    @Query(
        """
        SELECT n.id, n.title, substr(n.body, 1, 120) AS preview, n.state,
            length(CAST(n.title AS BLOB)) + length(CAST(n.body AS BLOB)) AS noteBytes,
            COUNT(v.id) AS versionCount, COALESCE(SUM(v.sizeBytes), 0) AS versionBytes
        FROM notes n LEFT JOIN note_versions v ON v.noteId = n.id
        GROUP BY n.id
        ORDER BY noteBytes + versionBytes DESC, n.id
        """,
    )
    suspend fun storageRows(): List<NoteStorageRow>

    @Query("DELETE FROM notes")
    suspend fun deleteAll()
}
