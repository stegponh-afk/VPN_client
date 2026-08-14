package com.netbridge.app.vpn

import com.netbridge.app.model.VlessConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds the Xray-core JSON client config for a single [VlessConfig] server.
 * Consumed by [XrayTunnelEngine] once it's wired up to an actual Xray point —
 * this half of the integration has no native/AAR dependency, so it's fully
 * implemented (and unit-testable) already.
 *
 * Inbound is a local SOCKS proxy on 127.0.0.1:10808; a tun2socks layer is
 * expected to bridge the VpnService TUN fd to that port.
 */
object VlessTunnelConfigBuilder {

    const val LOCAL_SOCKS_PORT = 10808

    fun build(config: VlessConfig): String {
        val root = JSONObject()

        root.put("log", JSONObject().put("loglevel", "warning"))

        root.put(
            "inbounds",
            JSONArray().put(
                JSONObject().apply {
                    put("tag", "socks-in")
                    put("listen", "127.0.0.1")
                    put("port", LOCAL_SOCKS_PORT)
                    put("protocol", "socks")
                    put("settings", JSONObject().put("udp", true))
                }
            )
        )

        root.put("outbounds", JSONArray().put(buildVlessOutbound(config)).put(buildDirectOutbound()))

        return root.toString()
    }

    private fun buildVlessOutbound(config: VlessConfig): JSONObject {
        val user = JSONObject().apply {
            put("id", config.id)
            put("encryption", config.encryption.ifBlank { "none" })
            if (config.flow.isNotBlank()) put("flow", config.flow)
        }

        val vnext = JSONObject().apply {
            put("address", config.address)
            put("port", config.port)
            put("users", JSONArray().put(user))
        }

        val streamSettings = JSONObject().apply {
            put("network", config.network.ifBlank { "tcp" })
            put("security", config.security.ifBlank { "none" })

            if (config.security == "tls") {
                put(
                    "tlsSettings",
                    JSONObject().apply {
                        put("serverName", config.sni)
                        if (config.fingerprint.isNotBlank()) put("fingerprint", config.fingerprint)
                        if (config.alpn.isNotBlank()) put("alpn", JSONArray(config.alpn.split(",")))
                    }
                )
            }

            if (config.security == "reality") {
                put(
                    "realitySettings",
                    JSONObject().apply {
                        put("serverName", config.sni)
                        put("publicKey", config.publicKey)
                        if (config.shortId.isNotBlank()) put("shortId", config.shortId)
                        if (config.spiderX.isNotBlank()) put("spiderX", config.spiderX)
                        if (config.fingerprint.isNotBlank()) put("fingerprint", config.fingerprint)
                    }
                )
            }

            when (config.network) {
                "ws" -> put(
                    "wsSettings",
                    JSONObject().apply {
                        put("path", config.path.ifBlank { "/" })
                        if (config.host.isNotBlank()) {
                            put("headers", JSONObject().put("Host", config.host))
                        }
                    }
                )
                "grpc" -> put(
                    "grpcSettings",
                    JSONObject().put("serviceName", config.serviceName)
                )
            }
        }

        return JSONObject().apply {
            put("tag", "proxy")
            put("protocol", "vless")
            put("settings", JSONObject().put("vnext", JSONArray().put(vnext)))
            put("streamSettings", streamSettings)
        }
    }

    private fun buildDirectOutbound(): JSONObject = JSONObject().apply {
        put("tag", "direct")
        put("protocol", "freedom")
    }
}
