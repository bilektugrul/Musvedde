package dev.aliarslan.musvedde.notification

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aliarslan.musvedde.MainActivity
import dev.aliarslan.musvedde.R
import dev.aliarslan.musvedde.data.note.NoteRepository
import dev.aliarslan.musvedde.data.note.OpenNoteTracker
import dev.aliarslan.musvedde.data.settings.SettingsRepository
import dev.aliarslan.musvedde.domain.Note
import dev.aliarslan.musvedde.domain.NoteState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android 16 normalizes a notification's big text: it drops zero-width and other invisible characters,
 * collapses whitespace and removes lines that end up empty. A braille blank is none of those, so a line
 * holding just that survives and is drawn as an empty line.
 */
private const val BLANK_LINE_MARKER = '\u2800'

fun keepBlankLines(text: String): String =
    text.trimEnd().lines().joinToString("\n") { line -> if (line.isBlank()) BLANK_LINE_MARKER.toString() else line }

@Singleton
class PinnedNoteNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val notes: NoteRepository,
    private val settings: SettingsRepository,
    private val openNotes: OpenNoteTracker,
) {
    private val manager = NotificationManagerCompat.from(context)

    private data class Shown(val title: String, val body: String)

    private val shown = ConcurrentHashMap<Long, Shown>()

    fun start(scope: CoroutineScope) {
        ensureChannel()
        scope.launch {
            combine(notes.observePinnedToNotification(), openNotes.openNotes, openNotes.foreground) { pinned, open, foreground ->
                pinned to if (foreground) open else emptySet()
            }.collect { (pinned, deferred) -> sync(pinned, deferred) }
        }
    }

    suspend fun repostAll() {
        shown.clear()
        sync(notes.observePinnedToNotification().first(), deferred = emptySet())
    }

    suspend fun handleDismissed(noteId: Long) {
        val note = notes.get(noteId)
        if (note == null || !note.pinnedToNotification || note.state != NoteState.ACTIVE) return
        if (settings.settings.first().restoreDismissedPins) {
            post(note)
        } else {
            notes.setPinnedToNotification(noteId, false)
        }
    }

    suspend fun handleUnpin(noteId: Long) {
        notes.setPinnedToNotification(noteId, false)
        shown.remove(noteId)
        manager.cancel(TAG, notificationId(noteId))
    }

    /**
     * Posts only notifications whose text changed. While the app is in front, a note open in the editor
     * keeps its current notification; it is refreshed once the note closes or the app goes to the background.
     */
    private fun sync(pinned: List<Note>, deferred: Set<Long>) {
        val wanted = pinned.associateBy { notificationId(it.id) }
        manager.activeNotifications
            .filter { it.tag == TAG && it.id !in wanted }
            .forEach { manager.cancel(TAG, it.id) }
        shown.keys.retainAll(pinned.map { it.id }.toSet())
        pinned.forEach { note ->
            val previous = shown[note.id]
            when {
                previous == Shown(note.title, note.body) -> Unit
                previous != null && note.id in deferred -> Unit
                else -> post(note)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun post(note: Note) {
        if (!hasNotificationPermission(context)) return
        val title = note.title.ifBlank { note.body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty() }
            .ifBlank { context.getString(R.string.notification_untitled) }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(note.body.lineSequence().firstOrNull { it.isNotBlank() && it != title })
            .setStyle(NotificationCompat.BigTextStyle().bigText(keepBlankLines(note.body)))
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent(note.id))
            .setDeleteIntent(actionIntent(PinActionReceiver.ACTION_DISMISSED, note.id))
            .addAction(0, context.getString(R.string.notification_action_open), openIntent(note.id))
            .addAction(0, context.getString(R.string.notification_action_unpin), actionIntent(PinActionReceiver.ACTION_UNPIN, note.id))
            .build()
        manager.notify(TAG, notificationId(note.id), notification)
        shown[note.id] = Shown(note.title, note.body)
    }

    private fun openIntent(noteId: Long): PendingIntent = PendingIntent.getActivity(
        context,
        notificationId(noteId),
        MainActivity.openNoteIntent(context, noteId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun actionIntent(action: String, noteId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        notificationId(noteId),
        Intent(context, PinActionReceiver::class.java).setAction(action).putExtra(PinActionReceiver.EXTRA_NOTE_ID, noteId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_pinned),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notification_channel_pinned_description)
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "pinned_notes"
        const val TAG = "pinned_note"
        private const val NOTIFICATION_ID_BASE = 10_000L

        fun notificationId(noteId: Long): Int = (NOTIFICATION_ID_BASE + noteId).toInt()
    }
}
