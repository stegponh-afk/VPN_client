package com.netbridge.app.vpn

import android.util.Log
import com.netbridge.app.model.VlessConfig
import libv2ray.CoreCallbackHandler
import libv2ray.CoreController
import libv2ray.Libv2ray

/**
 * Real [TunnelEngine], backed by Xray-core via `libv2ray.aar`
 * (github.com/2dust/AndroidLibXrayLite — see README.md → "Building the tunnel
 * engine" for how it's built).
 *
 * Xray-core has a native `tun` inbound (gVisor-based userspace network stack)
 * that reads/writes an already-open TUN fd directly — see
 * [VlessTunnelConfigBuilder], which emits that inbound instead of a local
 * SOCKS proxy. That means no separate tun2socks bridge is needed here.
 *
 * Xray-core's own outbound connection to the real VLESS server must not be
 * captured by our own TUN interface (the classic VPN routing-loop bug). The
 * usual fix is `VpnService.protect(fd)` per socket, but since Xray-core runs
 * as a Go library inside this same app process rather than a separate one,
 * the simpler fix is used instead: [CoreVpnService] excludes this app's own
 * package from the VPN via `Builder.addDisallowedApplication`, so all of this
 * process's traffic — including Xray-core's — already bypasses the tunnel.
 */
class XrayTunnelEngine : TunnelEngine {

    private var controller: CoreController? = null

    override var isRunning: Boolean = false
        private set

    override fun start(config: VlessConfig, tunFd: Int) {
        // Xray's geoip/geosite asset lookup (InitCoreEnv) is skipped — the JSON
        // config below has no geo-based routing rules to need it for.
        val json = VlessTunnelConfigBuilder.build(config)
        val newController = Libv2ray.newCoreController(LoggingCallbackHandler())
        controller = newController

        try {
            newController.startLoop(json, tunFd)
        } catch (e: Exception) {
            controller = null
            throw TunnelEngineException("Xray-core failed to start: ${e.message}", e)
        }
        isRunning = true
    }

    override fun stop() {
        val current = controller ?: return
        controller = null
        isRunning = false
        runCatching { current.stopLoop() }
            .onFailure { Log.w(TAG, "stopLoop failed", it) }
    }

    private class LoggingCallbackHandler : CoreCallbackHandler {
        override fun startup(): Long {
            Log.i(TAG, "Xray-core started")
            return 0
        }

        override fun shutdown(): Long {
            Log.i(TAG, "Xray-core stopped")
            return 0
        }

        override fun onEmitStatus(code: Long, message: String?): Long {
            Log.i(TAG, "Xray-core status [$code]: $message")
            return 0
        }
    }

    private companion object {
        const val TAG = "XrayTunnelEngine"
    }
}
