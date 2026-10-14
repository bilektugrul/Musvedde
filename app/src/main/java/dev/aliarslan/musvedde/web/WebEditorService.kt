package dev.aliarslan.musvedde.web

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.aliarslan.musvedde.MainActivity
import dev.aliarslan.musvedde.R
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Keeps the web editor running while the app is in the background, until the user stops it. */
@AndroidEntryPoint
class WebEditorService : Service() {
    @Inject lateinit var controller: WebEditorController

    private var scope: CoroutineScope? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            val running = scope
            if (running == null) {
                stopSelf()
            } else {
                running.launch {
                    controller.shutdown()
                    stopSelf()
                }
            }
            return START_NOT_STICKY
        }
        if (scope != null) return START_NOT_STICKY

        try {
            ensureChannel()
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(null), ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } catch (e: Exception) {
            controller.fail(e)
            stopSelf()
            return START_NOT_STICKY
        }
        @Suppress("DEPRECATION")
        wifiLock = getSystemService(WifiManager::class.java)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "musvedde:web-editor")
            .apply { acquire() }
        val failed = CoroutineExceptionHandler { _, cause ->
            controller.fail(cause)
            stopSelf()
        }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate + failed).also { scope ->
            controller.run(scope)
            scope.launch {
                controller.status.collect { status ->
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, notification((status as? WebEditorStatus.Running)?.address))
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        wifiLock?.takeIf { it.isHeld }?.release()
        controller.markStopped()
        super.onDestroy()
    }

    private fun notification(address: String?): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.web_editor_notification_title))
            .setContentText(address ?: getString(R.string.web_editor_notification_waiting))
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(
                PendingIntent.getActivity(
                    this,
                    0,
                    MainActivity.openWebEditorIntent(this),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .addAction(
                0,
                getString(R.string.web_editor_stop),
                PendingIntent.getService(
                    this,
                    0,
                    Intent(this, WebEditorService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.web_editor_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_STOP = "dev.aliarslan.musvedde.web.STOP"
        private const val CHANNEL_ID = "web_editor"
        private const val NOTIFICATION_ID = 7_001
    }
}
