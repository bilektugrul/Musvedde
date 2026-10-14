package dev.aliarslan.musvedde.web

import android.app.LocaleManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.aliarslan.musvedde.BuildConfig
import dev.aliarslan.musvedde.core.Clock
import dev.aliarslan.musvedde.core.ErrorLogger
import dev.aliarslan.musvedde.data.backup.KeepImporter
import dev.aliarslan.musvedde.data.backup.RestoreMode
import dev.aliarslan.musvedde.data.backup.RestoreService
import dev.aliarslan.musvedde.data.db.NoteDao
import dev.aliarslan.musvedde.data.note.LabelRepository
import dev.aliarslan.musvedde.data.note.NoteRepository
import dev.aliarslan.musvedde.data.note.VersionRepository
import dev.aliarslan.musvedde.data.settings.SettingsRepository
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

sealed interface WebEditorStatus {
    data object Stopped : WebEditorStatus
    data object Starting : WebEditorStatus
    data object NoWifi : WebEditorStatus
    data class Running(val address: String) : WebEditorStatus
    data object NoFreePort : WebEditorStatus
    data object Failed : WebEditorStatus
}

/** Runs the web editor's server for [WebEditorService] and tells the rest of the app how it is doing. */
@Singleton
class WebEditorController @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val noteDao: NoteDao,
    private val notes: NoteRepository,
    private val labels: LabelRepository,
    private val versions: VersionRepository,
    private val settings: SettingsRepository,
    private val keepImporter: KeepImporter,
    private val restoreService: RestoreService,
    private val clock: Clock,
    private val logger: ErrorLogger,
    val auth: WebAuth,
) {
    private val _status = MutableStateFlow<WebEditorStatus>(WebEditorStatus.Stopped)
    val status: StateFlow<WebEditorStatus> = _status.asStateFlow()

    private val closing = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val assets = ConcurrentHashMap<String, ByteArray>()
    private var server: EmbeddedServer<*, *>? = null
    private var host: String? = null

    fun start() {
        if (_status.value != WebEditorStatus.Stopped && _status.value != WebEditorStatus.Failed) return
        _status.value = WebEditorStatus.Starting
        runCatching { ContextCompat.startForegroundService(context, Intent(context, WebEditorService::class.java)) }
            .onFailure { fail(it) }
    }

    /** Something went wrong while starting or serving; the server goes down and the app says so. */
    internal fun fail(cause: Throwable) {
        logger.error("Web", "The web editor stopped: ${cause.message}", cause)
        markStopped()
        _status.value = WebEditorStatus.Failed
    }

    fun stop() {
        context.startService(Intent(context, WebEditorService::class.java).setAction(WebEditorService.ACTION_STOP))
    }

    /** Serves until [scope] is cancelled, following the phone from one Wi-Fi address to the next. */
    internal fun run(scope: CoroutineScope) {
        _status.value = WebEditorStatus.Starting
        auth.rotateCode()
        val sync = WebNoteSync(firstRevision = clock.now() * 1000)
        scope.launch { sync.follow(noteDao.observeWebNotes().map { rows -> rows.map { it.toWeb() } }) }
        val environment = environment(sync)
        scope.launch {
            wifiAddress().collectLatest { address ->
                stopServer()
                if (address == null) {
                    _status.value = WebEditorStatus.NoWifi
                    return@collectLatest
                }
                val port = withContext(Dispatchers.IO) { PORTS.firstOrNull { isFree(address, it) } }
                if (port == null) {
                    _status.value = WebEditorStatus.NoFreePort
                    return@collectLatest
                }
                host = "$address:$port"
                server = embeddedServer(CIO, host = address, port = port) { webEditor(environment) }
                    .also { withContext(Dispatchers.IO) { it.start(wait = false) } }
                _status.value = WebEditorStatus.Running("http://$address:$port")
            }
        }
    }

    internal suspend fun shutdown() {
        closing.tryEmit(Unit)
        delay(BYE_DELAY_MS)
        stopServer()
        _status.value = WebEditorStatus.Stopped
    }

    /** The service is gone, possibly killed by the system without [shutdown]. */
    internal fun markStopped() {
        val running = server
        server = null
        host = null
        running?.let { Thread { it.stop(gracePeriodMillis = 100, timeoutMillis = 1_000) }.start() }
        if (_status.value != WebEditorStatus.Failed) _status.value = WebEditorStatus.Stopped
    }

    private suspend fun stopServer() {
        val running = server ?: return
        server = null
        host = null
        withContext(Dispatchers.IO) { running.stop(gracePeriodMillis = 100, timeoutMillis = 1_000) }
    }

    private fun environment(sync: WebNoteSync) = WebEnvironment(
        notes = notes,
        labels = labels,
        versions = versions,
        auth = auth,
        sync = sync,
        theme = settings.settings.map { webTheme(context, it) }.distinctUntilChanged(),
        locale = ::appLanguage,
        appVersion = BuildConfig.VERSION_NAME,
        importKeep = { input ->
            val content = keepImporter.read(input)
            val added = restoreService.restore(content, RestoreMode.MERGE).getOrThrow()
            ImportResult(added = added, skipped = content.notes.size - added)
        },
        asset = { path ->
            assets[path] ?: runCatching { context.assets.open("web/$path").use { it.readBytes() } }.getOrNull()
                ?.also { assets[path] = it }
        },
        expectedHost = { host },
        closing = closing,
        logger = logger,
    )

    private fun appLanguage(): String {
        val appLocales = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales
        } else {
            null
        }
        val locale = appLocales?.takeUnless { it.isEmpty }?.get(0) ?: Resources.getSystem().configuration.locales[0]
        return if (locale.language == "tr") "tr" else "en"
    }

    private fun wifiAddress(): Flow<String?> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val addresses = ConcurrentHashMap<Network, String>()
        fun publish() {
            trySend(addresses.values.minOrNull())
        }
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLinkPropertiesChanged(network: Network, properties: LinkProperties) {
                val address = properties.linkAddresses.map { it.address }
                    .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
                if (address == null) addresses.remove(network) else addresses[network] = address
                publish()
            }

            override fun onLost(network: Network) {
                addresses.remove(network)
                publish()
            }
        }
        val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        manager.registerNetworkCallback(request, callback)
        publish()
        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun isFree(address: String, port: Int): Boolean = try {
        ServerSocket().use { it.bind(InetSocketAddress(address, port)) }
        true
    } catch (_: IOException) {
        false
    }

    private companion object {
        val PORTS = 8080..8090
        const val BYE_DELAY_MS = 300L
    }
}
