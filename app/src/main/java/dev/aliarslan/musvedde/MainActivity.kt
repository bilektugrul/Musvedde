package dev.aliarslan.musvedde

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.aliarslan.musvedde.ui.MainViewModel
import dev.aliarslan.musvedde.ui.MusveddeNavHost
import dev.aliarslan.musvedde.ui.theme.MusveddeTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val openNoteRequests = Channel<Long>(Channel.CONFLATED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val labels by viewModel.labels.collectAsStateWithLifecycle()
            val folders by viewModel.folders.collectAsStateWithLifecycle()
            val loaded = settings ?: return@setContent
            MusveddeTheme(loaded) {
                Surface(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }, color = MaterialTheme.colorScheme.background) {
                    MusveddeNavHost(labels = labels, folders = folders, openNoteRequests = openNoteRequests.receiveAsFlow())
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val noteId = intent?.getLongExtra(EXTRA_NOTE_ID, 0L) ?: 0L
        if (noteId > 0) {
            openNoteRequests.trySend(noteId)
            intent?.removeExtra(EXTRA_NOTE_ID)
        }
    }

    companion object {
        const val EXTRA_NOTE_ID = "dev.aliarslan.musvedde.NOTE_ID"

        fun openNoteIntent(context: Context, noteId: Long) =
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_NOTE_ID, noteId)
    }
}
