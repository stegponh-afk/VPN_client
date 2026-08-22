package com.netbridge.app.subscription

import android.util.Base64
import com.netbridge.app.model.VlessConfig
import java.net.URI
import java.net.URLDecoder

/**
 * Parses the content of a subscription URL into a list of [VlessConfig].
 *
 * Subscription content is conventionally either:
 *  - base64 of newline-separated `vless://...` links, or
 *  - plain newline-separated `vless://...` links.
 * Lines using other protocols (vmess://, trojan://, ss://) are ignored — this
 * client intentionally only supports VLESS, per the brief.
 */
object SubscriptionParser {

    fun parse(rawContent: String): List<VlessConfig> {
        val decoded = decodeIfBase64(rawContent.trim())
        return decoded
            .lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("vless://") }
            .mapNotNull { runCatching { parseVlessUri(it) }.getOrNull() }
            .toList()
    }

    private fun decodeIfBase64(content: String): String {
        if (content.startsWith("vless://")) return content
        return runCatching {
            val normalized = content.replace("\n", "").replace("\r", "")
            String(Base64.decode(normalized, Base64.DEFAULT), Charsets.UTF_8)
        }.getOrDefault(content)
    }

    private fun parseVlessUri(link: String): VlessConfig {
        val uri = URI(link)
        require(uri.scheme == "vless") { "Not a vless link" }

        val id = uri.userInfo ?: error("Missing uuid")
        val address = uri.host ?: error("Missing host")
        val port = if (uri.port != -1) uri.port else 443

        val params = parseQuery(uri.rawQuery.orEmpty())
        val remark = uri.rawFragment
            ?.let { URLDecoder.decode(it, "UTF-8") }
            ?.takeIf { it.isNotBlank() }
            ?: "$address:$port"

        return VlessConfig(
            id = id,
            remark = remark,
            address = address,
            port = port,
            encryption = params["encryption"] ?: "none",
            flow = params["flow"] ?: "",
            security = params["security"] ?: "none",
            sni = params["sni"] ?: "",
            fingerprint = params["fp"] ?: "",
            publicKey = params["pbk"] ?: "",
            shortId = params["sid"] ?: "",
            spiderX = params["spx"] ?: "",
            network = params["type"] ?: "tcp",
            headerType = params["headerType"] ?: "",
            host = params["host"] ?: "",
            path = params["path"] ?: "",
            serviceName = params["serviceName"] ?: "",
            alpn = params["alpn"] ?: "",
            mode = params["mode"] ?: "",
            extraJson = params["extra"] ?: "",
            rawUri = link,
        )
    }

    private fun parseQuery(rawQuery: String): Map<String, String> {
        if (rawQuery.isBlank()) return emptyMap()
        return rawQuery.split("&")
            .mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx == -1) return@mapNotNull null
                val key = pair.substring(0, idx)
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                key to value
            }
            .toMap()
    }
}
