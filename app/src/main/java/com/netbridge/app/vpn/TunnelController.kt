package com.netbridge.app.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService
import com.netbridge.app.model.VlessConfig

/**
 * Thin façade the Activity talks to: handles the VpnService.prepare() consent
 * flow and starting/stopping [CoreVpnService]. Actual state lives in [TunnelStatus].
 */
class TunnelController(private val context: Context) {

    /** Returns an Intent to launch for system VPN consent, or null if already granted. */
    fun prepareConsentIntent(): Intent? = VpnService.prepare(context)

    fun connect(config: VlessConfig) {
        val intent = ConfigExtras.write(Intent(context, CoreVpnService::class.java), config)
        context.startService(intent)
    }

    fun disconnect() {
        val intent = Intent(context, CoreVpnService::class.java).apply {
            action = CoreVpnService.ACTION_DISCONNECT
        }
        context.startService(intent)
    }
}
