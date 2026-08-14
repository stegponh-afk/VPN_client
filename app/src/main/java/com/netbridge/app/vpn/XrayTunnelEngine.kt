package com.netbridge.app.vpn

import com.netbridge.app.model.VlessConfig
import java.io.FileDescriptor

/**
 * Real [TunnelEngine] backed by Xray-core, once `libv2ray.aar` is built and added
 * to `app/libs/` (see README.md → "Building the tunnel engine"). This file is a
 * scaffold, not a working implementation — it deliberately does NOT reference
 * `libv2ray.*` classes so the project keeps compiling before that AAR exists.
 *
 * Once you've added the AAR and Android Studio can resolve `libv2ray.Libv2ray`,
 * fill in the TODOs below. The shape mirrors how AndroidLibXrayLite/v2rayNG wire
 * an Xray point up to a VpnService — three moving pieces:
 *
 *  1. Build a JSON Xray client config from [VlessConfig] (inbound: socks/http on
 *     localhost; outbound: vless with this server's address/port/id/flow/
 *     network/security/sni/reality-or-tls params). [VlessTunnelConfigBuilder]
 *     already does this transform for you.
 *
 *  2. Implement the callback interface Xray expects from the host app (commonly
 *     named something like `V2RayVPNServiceSupportsSet`) with methods equivalent
 *     to: `protect(fd: Int): Boolean` → forward to [protectSocket]; `setup(...)`
 *     → no-op, this app builds the tun interface itself in CoreVpnService;
 *     `shutdown()` → mark [isRunning] false; `onEmitStatus(...)` → optional
 *     logging.
 *
 *  3. Create the point (`Libv2ray.newXrayPoint(callback)` or similar in the
 *     version you vendor), feed it the JSON config, call its run/loop method.
 *     Xray then listens on a local SOCKS port; bridge the [tunFd] to that SOCKS
 *     port with a tun2socks implementation — AndroidLibXrayLite historically
 *     ships one, or use github.com/xjasonlyu/tun2socks / hev-socks5-tunnel.
 *
 * Until this is filled in, [CoreVpnService] uses [StubTunnelEngine] instead.
 */
class XrayTunnelEngine : TunnelEngine {

    override var isRunning: Boolean = false
        private set

    override fun start(config: VlessConfig, tunFd: FileDescriptor, protectSocket: (Int) -> Boolean) {
        // TODO: build JSON config
        // val json = VlessTunnelConfigBuilder.build(config)
        //
        // TODO: construct the Xray point with a callback that forwards protect()
        // calls to `protectSocket`, start it, then start tun2socks against tunFd
        // pointed at Xray's local SOCKS inbound.
        throw TunnelEngineException(
            "XrayTunnelEngine is a scaffold — see the class kdoc for the three " +
                "integration steps still to implement."
        )
    }

    override fun stop() {
        // TODO: stop tun2socks, then point.StopLoop() / equivalent.
        isRunning = false
    }
}
