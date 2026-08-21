package com.netbridge.app.subscription

import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * DNS-over-HTTPS resolver used as a fallback when plain system DNS fails to
 * resolve a subscription host (`UnknownHostException`) — the common symptom of
 * ISP/operator-level DNS blocking of a domain, independent of whether the IP
 * itself is reachable. Queries go to a DoH endpoint by IP literal, not hostname,
 * so this doesn't depend on system DNS working for the DoH provider either.
 */
object DohResolver {

    // (name, IP literal, path) — IP literal so this doesn't itself depend on DNS.
    private val PROVIDERS = listOf(
        Triple("cloudflare-dns.com", "104.16.248.249", "/dns-query"),
        Triple("dns.google", "8.8.8.8", "/resolve"),
    )

    /** Returns the first A-record IP for [host], or null if every provider fails. */
    fun resolve(host: String): String? {
        for ((sniHost, ip, path) in PROVIDERS) {
            val ip4 = runCatching { queryOne(sniHost, ip, path, host) }.getOrNull()
            if (ip4 != null) return ip4
        }
        return null
    }

    private fun queryOne(sniHost: String, ip: String, path: String, targetHost: String): String? {
        val url = URL("https://$ip$path?name=$targetHost&type=A")
        val connection = url.openConnection() as HttpsURLConnection
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("Accept", "application/dns-json")
        // Connect to the DoH provider by IP but keep SNI/hostname verification on
        // its real name — this is the standard "connect-by-IP, verify-by-name" TLS
        // pattern, it does not weaken certificate validation.
        connection.sslSocketFactory = SniPinnedSocketFactory(sniHost)
        connection.setRequestProperty("Host", sniHost)

        return try {
            check(connection.responseCode == 200) { "HTTP ${connection.responseCode}" }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseFirstA(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseFirstA(dnsJson: String): String? {
        val answers = JSONObject(dnsJson).optJSONArray("Answer") ?: return null
        for (i in 0 until answers.length()) {
            val entry = answers.getJSONObject(i)
            if (entry.optInt("type") == 1) { // type 1 = A record
                return entry.optString("data").takeIf { it.isNotBlank() }
            }
        }
        return null
    }
}
