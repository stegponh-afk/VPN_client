package com.netbridge.app.vpn

import com.netbridge.app.model.VlessConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds the Xray-core JSON client config for a single [VlessConfig] server.
 *
 * Inbound is Xray's native `tun` protocol: it reads/writes an already-open TUN
 * fd directly via a bundled gVisor userspace network stack (see
 * proxy/tun/tun_android.go in xray-core) — [XrayTunnelEngine] passes the fd
 * through the `xray.tun.fd` env var (CoreController.startLoop's second arg),
 * so this config only needs to declare the inbound exists, no port/listen.
 */
object VlessTunnelConfigBuilder {

    fun build(config: VlessConfig): String {
        val root = JSONObject()

        root.put("log", JSONObject().put("loglevel", "warning"))

        root.put(
            "inbounds",
            JSONArray().put(
                JSONObject().apply {
                    put("tag", "tun-in")
                    put("protocol", "tun")
                    put("settings", JSONObject().put("mtu", 1500))
                    put(
                        "sniffing",
                        JSONObject().apply {
                            put("enabled", true)
                            put("destOverride", JSONArray().put("http").put("tls").put("quic"))
                            put("routeOnly", true)
                        }
                    )
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
                "xhttp" -> put(
                    "xhttpSettings",
                    JSONObject().apply {
                        put("path", config.path.ifBlank { "/" })
                        if (config.host.isNotBlank()) put("host", config.host)
                        put("mode", config.mode.ifBlank { "auto" })
                        if (config.extraJson.isNotBlank()) {
                            runCatching { put("extra", JSONObject(config.extraJson)) }
                        }
                    }
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
