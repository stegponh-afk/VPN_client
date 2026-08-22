package com.netbridge.app.vpn

import com.netbridge.app.model.VlessConfig

/** Used only when `libv2ray.aar` isn't present at build time — see README.md. */
class StubTunnelEngine : TunnelEngine {

    override var isRunning: Boolean = false
        private set

    override fun start(config: VlessConfig, tunFd: Int) {
        throw TunnelEngineException(
            "Xray tunnel engine is not built into this app. " +
                "Build libv2ray.aar (see README.md) and drop it into app/libs/."
        )
    }

    override fun stop() {
        isRunning = false
    }
}
