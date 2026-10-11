package dev.aliarslan.musvedde.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.aliarslan.musvedde.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PinActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notifier: PinnedNoteNotifier

    @Inject
    @ApplicationScope
    lateinit var appScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L).takeIf { it > 0 } ?: return
        val pending = goAsync()
        appScope.launch {
            try {
                when (intent.action) {
                    ACTION_UNPIN -> notifier.handleUnpin(noteId)
                    ACTION_DISMISSED -> notifier.handleDismissed(noteId)
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_UNPIN = "dev.aliarslan.musvedde.action.UNPIN"
        const val ACTION_DISMISSED = "dev.aliarslan.musvedde.action.DISMISSED"
        const val EXTRA_NOTE_ID = "noteId"
    }
}
