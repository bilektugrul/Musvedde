package dev.aliarslan.musvedde.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ErrorLogDao {

    @Query("SELECT * FROM error_log ORDER BY timestamp DESC, id DESC")
    fun observeAll(): Flow<List<ErrorLogEntity>>

    @Insert
    suspend fun insert(entry: ErrorLogEntity): Long

    @Query("DELETE FROM error_log WHERE id NOT IN (SELECT id FROM error_log ORDER BY timestamp DESC, id DESC LIMIT :max)")
    suspend fun pruneTo(max: Int)

    @Query("DELETE FROM error_log")
    suspend fun clear()
}
