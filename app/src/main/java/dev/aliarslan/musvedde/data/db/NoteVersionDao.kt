package dev.aliarslan.musvedde.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteVersionDao {

    @Insert
    suspend fun insert(version: NoteVersionEntity): Long

    @Insert
    suspend fun insertAll(versions: List<NoteVersionEntity>)

    @Query("SELECT * FROM note_versions WHERE noteId = :noteId ORDER BY createdAt DESC, id DESC")
    fun observeForNote(noteId: Long): Flow<List<NoteVersionEntity>>

    @Query("SELECT * FROM note_versions WHERE noteId = :noteId ORDER BY createdAt DESC, id DESC LIMIT 1")
    suspend fun latestForNote(noteId: Long): NoteVersionEntity?

    @Query("SELECT COUNT(*) FROM note_versions WHERE noteId = :noteId")
    suspend fun countForNote(noteId: Long): Int

    @Query("SELECT * FROM note_versions WHERE id = :id")
    suspend fun getById(id: Long): NoteVersionEntity?

    @Query("SELECT * FROM note_versions WHERE noteId IN (:noteIds) ORDER BY createdAt, id")
    suspend fun forNotes(noteIds: List<Long>): List<NoteVersionEntity>

    @Query("SELECT COUNT(*) AS count, COALESCE(SUM(sizeBytes), 0) AS bytes FROM note_versions")
    suspend fun totals(): VersionTotals

    @Query("DELETE FROM note_versions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM note_versions WHERE noteId = :noteId")
    suspend fun deleteForNote(noteId: Long)

    @Query("DELETE FROM note_versions")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPending(pending: PendingVersionEntity)

    @Query("SELECT * FROM pending_versions WHERE noteId = :noteId")
    suspend fun pending(noteId: Long): PendingVersionEntity?

    @Query("SELECT * FROM pending_versions")
    suspend fun allPending(): List<PendingVersionEntity>

    @Query("DELETE FROM pending_versions WHERE noteId = :noteId")
    suspend fun deletePending(noteId: Long)
}
