package com.netbridge.app.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.netbridge.app.MainActivity
import com.netbridge.app.R
import com.netbridge.app.model.VlessConfig

/**
 * The actual VPN process. Android requires a foreground notification and shows
 * its own persistent status-bar key icon for as long as this is active — that is
 * an OS transparency guarantee for the device owner and is not something this
 * app suppresses (see README → "Notes on VPN visibility").
 */
class CoreVpnService : VpnService() {

    private var tunInterface: ParcelFileDescriptor? = null
    private val engine: TunnelEngine = StubTunnelEngine() // swap for XrayTunnelEngine() once it's implemented

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            stopTunnel()
            return START_NOT_STICKY
        }

        val config = intent?.let { ConfigExtras.read(it) }
        if (config == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification(connecting = true))
        TunnelStatus.update(TunnelState.CONNECTING)

        try {
            establishTunnel(config)
        } catch (e: TunnelEngineException) {
            TunnelStatus.update(TunnelState.DISCONNECTED, error = e.message)
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun establishTunnel(config: VlessConfig) {
        val builder = Builder()
            .setSession(getString(R.string.app_name))
            .addAddress("10.10.10.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("1.1.1.1")
            .addDnsServer("8.8.8.8")
            .setMtu(1500)

        val pfd = builder.establish() ?: throw TunnelEngineException("VpnService.Builder.establish() returned null")
        tunInterface = pfd

        engine.start(config, pfd.fileDescriptor) { fd -> protect(fd) }

        TunnelStatus.update(TunnelState.CONNECTED)
        updateNotification(connecting = false)
    }

    private fun stopTunnel() {
        engine.stop()
        tunInterface?.close()
        tunInterface = null
        TunnelStatus.update(TunnelState.DISCONNECTED)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onRevoke() {
        // User revoked VPN permission from system settings (e.g. another VPN app took over).
        stopTunnel()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopTunnel()
        super.onDestroy()
    }

    private fun buildNotification(connecting: Boolean): Notification {
        ensureChannel()
        val openApp = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val text = if (connecting) getString(R.string.status_connecting) else getString(R.string.status_connected)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentIntent(openApp)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(connecting: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(connecting))
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.vpn_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    companion object {
        const val ACTION_DISCONNECT = "com.netbridge.app.action.DISCONNECT"
        private const val CHANNEL_ID = "netbridge_vpn_status"
        private const val NOTIFICATION_ID = 1
    }
}
