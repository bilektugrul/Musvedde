package dev.aliarslan.musvedde.data.db

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlin.coroutines.CoroutineContext

fun inMemoryDatabase(queryContext: CoroutineContext? = null): MusveddeDatabase =
    Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MusveddeDatabase::class.java)
        .allowMainThreadQueries()
        .apply { if (queryContext != null) setQueryCoroutineContext(queryContext) }
        .build()

fun noteEntity(
    title: String = "",
    body: String = "",
    state: String = "ACTIVE",
    pinnedToTop: Boolean = false,
    position: Long = 0,
    updatedAt: Long = 0,
    trashedAt: Long? = null,
) = NoteEntity(
    title = title,
    body = body,
    color = "DEFAULT",
    pinnedToTop = pinnedToTop,
    pinnedToNotification = false,
    state = state,
    position = position,
    createdAt = 0,
    updatedAt = updatedAt,
    trashedAt = trashedAt,
)
