package dev.aliarslan.musvedde.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WebDeviceDao {

    @Query("SELECT * FROM web_devices ORDER BY lastSeenAt DESC")
    fun observeAll(): Flow<List<WebDeviceEntity>>

    @Query("SELECT * FROM web_devices WHERE tokenHash = :tokenHash")
    suspend fun byTokenHash(tokenHash: String): WebDeviceEntity?

    @Insert
    suspend fun insert(device: WebDeviceEntity): Long

    @Query("UPDATE web_devices SET lastSeenAt = :at WHERE id = :id")
    suspend fun touch(id: Long, at: Long)

    @Query("DELETE FROM web_devices WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM web_devices")
    suspend fun deleteAll()
}
