package dev.aliarslan.musvedde.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A computer paired with the web editor. Only the SHA-256 of its key is kept. */
@Entity(tableName = "web_devices", indices = [Index(value = ["tokenHash"], unique = true)])
data class WebDeviceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val tokenHash: String,
    val createdAt: Long,
    val lastSeenAt: Long,
)
