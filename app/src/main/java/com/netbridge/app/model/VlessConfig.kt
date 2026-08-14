package com.netbridge.app.model

/**
 * Parsed representation of a single `vless://` share link, as produced by the
 * VLESS subscription format used by Xray-core panels (3x-ui, Marzban, etc.).
 */
data class VlessConfig(
    val id: String,
    val remark: String,
    val address: String,
    val port: Int,
    val encryption: String,
    val flow: String,
    val security: String,
    val sni: String,
    val fingerprint: String,
    val publicKey: String,
    val shortId: String,
    val spiderX: String,
    val network: String,
    val headerType: String,
    val host: String,
    val path: String,
    val serviceName: String,
    val alpn: String,
    val rawUri: String,
) {
    /** Stable identity for storage/selection — same server, different remark, is still the same server. */
    val key: String
        get() = "$id@$address:$port"
}
