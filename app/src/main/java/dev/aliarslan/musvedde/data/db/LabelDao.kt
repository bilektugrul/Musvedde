package dev.aliarslan.musvedde.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LabelDao {

    @Query("SELECT * FROM labels ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<LabelEntity>>

    @Insert
    suspend fun insert(label: LabelEntity): Long

    @Query("UPDATE labels SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM labels WHERE id = :id")
    suspend fun delete(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRefs(refs: List<NoteLabelCrossRef>)

    suspend fun addToNotes(labelId: Long, noteIds: List<Long>) =
        insertCrossRefs(noteIds.map { NoteLabelCrossRef(noteId = it, labelId = labelId) })

    @Query("DELETE FROM note_labels WHERE labelId = :labelId AND noteId IN (:noteIds)")
    suspend fun removeFromNotes(labelId: Long, noteIds: List<Long>)

    @Query("SELECT labelId FROM note_labels WHERE noteId = :noteId")
    suspend fun labelIdsFor(noteId: Long): List<Long>

    @Query("SELECT * FROM labels")
    suspend fun all(): List<LabelEntity>

    @Query("SELECT * FROM note_labels")
    suspend fun allCrossRefs(): List<NoteLabelCrossRef>

    @Query("DELETE FROM labels")
    suspend fun deleteAll()
}
