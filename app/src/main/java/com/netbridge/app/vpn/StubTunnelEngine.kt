package com.netbridge.app.vpn

import com.netbridge.app.model.VlessConfig
import java.io.FileDescriptor

/**
 * Default [TunnelEngine] wired in until the real Xray-core engine is built and
 * linked (see README.md → "Building the tunnel engine"). It deliberately refuses
 * to report success — a prototype that silently shows "Connected" while pushing
 * zero packets would be worse than one that's honest about not being wired up yet.
 */
class StubTunnelEngine : TunnelEngine {

    override var isRunning: Boolean = false
        private set

    override fun start(config: VlessConfig, tunFd: FileDescriptor, protectSocket: (Int) -> Boolean) {
        throw TunnelEngineException(
            "Xray tunnel engine is not built into this app yet. " +
                "Build libv2ray.aar (see README.md) and implement XrayTunnelEngine, " +
                "then wire it in CoreVpnService.engine."
        )
    }

    override fun stop() {
        isRunning = false
    }
}
