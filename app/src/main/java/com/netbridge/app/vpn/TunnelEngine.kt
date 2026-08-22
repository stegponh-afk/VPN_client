package com.netbridge.app.vpn

import com.netbridge.app.model.VlessConfig

/**
 * Abstraction over "whatever actually speaks the VLESS protocol and pushes packets
 * through the TUN file descriptor". Kept as an interface so the rest of the app
 * (UI, service lifecycle, notification, HWID/subscription logic) does not depend
 * on which engine implementation is wired in.
 *
 * Real implementation: [XrayTunnelEngine], backed by `libv2ray.aar` (built from
 * github.com/2dust/AndroidLibXrayLite — see README.md → "Building the tunnel
 * engine"). [StubTunnelEngine] is used instead when that AAR isn't present, and
 * reports [TunnelEngineException] rather than silently pretending to be connected.
 */
interface TunnelEngine {

    /**
     * Start routing traffic for [config] through the TUN interface identified by
     * [tunFd] (the raw fd int from `ParcelFileDescriptor.getFd()`).
     */
    @Throws(TunnelEngineException::class)
    fun start(config: VlessConfig, tunFd: Int)

    fun stop()

    val isRunning: Boolean
}

class TunnelEngineException(message: String, cause: Throwable? = null) : Exception(message, cause)
