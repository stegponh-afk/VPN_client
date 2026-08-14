package com.netbridge.app.vpn

import com.netbridge.app.model.VlessConfig
import java.io.FileDescriptor

/**
 * Abstraction over "whatever actually speaks the VLESS protocol and pushes packets
 * through the TUN file descriptor". Kept as an interface so the rest of the app
 * (UI, service lifecycle, notification, HWID/subscription logic) does not depend
 * on which engine implementation is wired in.
 *
 * The only real-world implementation of VLESS today is Xray-core (Go). There is no
 * prebuilt Maven/JitPack artifact for it — see README.md, section
 * "Building the tunnel engine", for how to produce `libv2ray.aar` and wire a
 * concrete [TunnelEngine] against it. Until that's done, [StubTunnelEngine] is
 * used and reports [TunnelEngineException] instead of silently pretending to be
 * connected.
 */
interface TunnelEngine {

    /**
     * Start routing traffic for [config] through [tunFd].
     * [protectSocket] MUST be called (this is `VpnService.protect(fd)`) on the
     * engine's own outbound socket before it connects out, otherwise the engine's
     * own connection to the VLESS server gets captured by the TUN interface and
     * the device loses network entirely (classic VPN-app routing loop).
     */
    @Throws(TunnelEngineException::class)
    fun start(config: VlessConfig, tunFd: FileDescriptor, protectSocket: (Int) -> Boolean)

    fun stop()

    val isRunning: Boolean
}

class TunnelEngineException(message: String, cause: Throwable? = null) : Exception(message, cause)
