package dev.aliarslan.musvedde.notification

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import dev.aliarslan.musvedde.data.db.MusveddeDatabase
import dev.aliarslan.musvedde.data.db.inMemoryDatabase
import dev.aliarslan.musvedde.data.note.NoteRepository
import dev.aliarslan.musvedde.data.note.OpenNoteTracker
import dev.aliarslan.musvedde.data.settings.InMemoryPreferencesDataStore
import dev.aliarslan.musvedde.data.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class PinnedNoteNotifierTest {
    private val dispatcher = StandardTestDispatcher()
    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private lateinit var db: MusveddeDatabase
    private lateinit var notes: NoteRepository
    private val settings = SettingsRepository(InMemoryPreferencesDataStore())
    private val tracker = OpenNoteTracker()
    private lateinit var notifier: PinnedNoteNotifier

    @Before
    fun setUp() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        db = inMemoryDatabase(dispatcher)
        notes = NoteRepository(db, clock = { 1L }, io = dispatcher)
        notifier = PinnedNoteNotifier(context, notes, settings, tracker)
        notifier.start(scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun posted(): List<Notification> = shadowOf(manager).allNotifications

    private fun Notification.bigText() = extras.getCharSequence(NotificationCompat.EXTRA_BIG_TEXT)?.toString()

    @Test
    fun postsNotificationForPinnedNote() = runTest(dispatcher) {
        val id = notes.create("Gece", "ilk satır\nikinci satır")
        notes.setPinnedToNotification(id, true)
        advanceUntilIdle()

        val notification = posted().single()
        assertThat(notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE).toString()).isEqualTo("Gece")
        assertThat(notification.bigText()).isEqualTo("ilk satır\nikinci satır")
        assertThat(notification.flags and Notification.FLAG_ONGOING_EVENT).isNotEqualTo(0)
        assertThat(notification.deleteIntent).isNotNull()
    }

    @Test
    fun editUpdatesSameNotificationId() = runTest(dispatcher) {
        val id = notes.create("", "eski")
        notes.setPinnedToNotification(id, true)
        advanceUntilIdle()

        notes.updateContent(id, "", "yeni")
        advanceUntilIdle()

        assertThat(posted()).hasSize(1)
        assertThat(posted().single().bigText()).isEqualTo("yeni")
    }

    @Test
    fun unpinCancels() = runTest(dispatcher) {
        val id = notes.create("", "satır")
        notes.setPinnedToNotification(id, true)
        advanceUntilIdle()

        notes.setPinnedToNotification(id, false)
        advanceUntilIdle()

        assertThat(posted()).isEmpty()
    }

    @Test
    fun trashingCancels() = runTest(dispatcher) {
        val id = notes.create("", "satır")
        notes.setPinnedToNotification(id, true)
        advanceUntilIdle()

        notes.trash(listOf(id))
        advanceUntilIdle()

        assertThat(posted()).isEmpty()
    }

    @Test
    fun dismissWithRestoreOnReposts() = runTest(dispatcher) {
        val id = notes.create("", "satır")
        notes.setPinnedToNotification(id, true)
        advanceUntilIdle()
        manager.cancelAll()

        notifier.handleDismissed(id)
        advanceUntilIdle()

        assertThat(posted()).hasSize(1)
        assertThat(notes.get(id)!!.pinnedToNotification).isTrue()
    }

    @Test
    fun dismissWithRestoreOffUnpins() = runTest(dispatcher) {
        settings.update { it.copy(restoreDismissedPins = false) }
        val id = notes.create("", "satır")
        notes.setPinnedToNotification(id, true)
        advanceUntilIdle()
        manager.cancelAll()

        notifier.handleDismissed(id)
        advanceUntilIdle()

        assertThat(posted()).isEmpty()
        assertThat(notes.get(id)!!.pinnedToNotification).isFalse()
    }

    @Test
    fun blankLinesSurviveTheSystemNormalizer() = runTest(dispatcher) {
        val id = notes.create("", "X\n\nY\n\n\nZ\n")
        notes.setPinnedToNotification(id, true)
        advanceUntilIdle()

        val shown = systemNormalized(posted().single().bigText()!!)
        assertThat(shown.lines().map { it.isBlankLooking() }).containsExactly(false, true, false, true, true, false).inOrder()
    }

    @Test
    fun keepBlankLinesLeavesOtherLinesAlone() {
        assertThat(keepBlankLines("a\n  \nb").lines()[0]).isEqualTo("a")
        assertThat(keepBlankLines("a\n  \nb").lines()[2]).isEqualTo("b")
        assertThat(keepBlankLines("tek satır")).isEqualTo("tek satır")
        assertThat(keepBlankLines("son\n\n")).isEqualTo("son")
    }

    private fun String.isBlankLooking() = all { it == '⠀' }

    // Mirrors the rules Android 16's notification big text normalizer applies: invisible and zero-width
    // characters are dropped, whitespace runs become one space, lines are trimmed and empty lines removed.
    private fun systemNormalized(text: String): String = text
        .split(Regex("[\\n\\r\\u000B\\u000C\\u0085\\u2028\\u2029]+"))
        .map { line ->
            line.filterNot { it in '​'..'‍' || it == '﻿' || it == '͏' || it in '⁠'..'⁥' || it in '⁪'..'⁯' || it in '￹'..'￻' }
                .replace(Regex("\\p{Zs}+"), " ")
                .trim()
        }
        .filter { it.isNotEmpty() }
        .joinToString("\n")

    @Test
    fun editingAnOpenNoteDefersTheUpdateUntilItCloses() = runTest(dispatcher) {
        tracker.setForeground(true)
        val id = notes.create("", "eski")
        notes.setPinnedToNotification(id, true)
        advanceUntilIdle()

        tracker.open(id)
        notes.updateContent(id, "", "yeni")
        advanceUntilIdle()
        assertThat(posted().single().bigText()).isEqualTo("eski")

        tracker.close(id)
        advanceUntilIdle()
        assertThat(posted().single().bigText()).isEqualTo("yeni")
    }

    @Test
    fun leavingTheAppPostsTheDeferredUpdate() = runTest(dispatcher) {
        tracker.setForeground(true)
        val id = notes.create("", "eski")
        notes.setPinnedToNotification(id, true)
        advanceUntilIdle()
        tracker.open(id)
        notes.updateContent(id, "", "yeni")
        advanceUntilIdle()

        tracker.setForeground(false)
        advanceUntilIdle()

        assertThat(posted().single().bigText()).isEqualTo("yeni")
    }

    @Test
    fun pinningAnOpenNotePostsImmediately() = runTest(dispatcher) {
        tracker.setForeground(true)
        val id = notes.create("", "metin")
        tracker.open(id)

        notes.setPinnedToNotification(id, true)
        advanceUntilIdle()

        assertThat(posted().single().bigText()).isEqualTo("metin")
    }

    @Test
    fun editingOneNoteLeavesOtherNotificationsAlone() = runTest(dispatcher) {
        val edited = notes.create("", "a")
        val other = notes.create("", "b")
        notes.setPinnedToNotification(edited, true)
        notes.setPinnedToNotification(other, true)
        advanceUntilIdle()
        val before = shadowOf(manager).getNotification(PinnedNoteNotifier.TAG, PinnedNoteNotifier.notificationId(other))

        notes.updateContent(edited, "", "a2")
        advanceUntilIdle()

        val after = shadowOf(manager).getNotification(PinnedNoteNotifier.TAG, PinnedNoteNotifier.notificationId(other))
        assertThat(after).isSameInstanceAs(before)
        val editedNow = shadowOf(manager).getNotification(PinnedNoteNotifier.TAG, PinnedNoteNotifier.notificationId(edited))
        assertThat(editedNow.bigText()).isEqualTo("a2")
    }
}
