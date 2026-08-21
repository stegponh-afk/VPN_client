package com.netbridge.app.subscription

import com.netbridge.app.model.VlessConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection

/**
 * Fetches the subscription content over HTTPS and hands it to [SubscriptionParser].
 *
 * The device id is sent both as a header and as a query parameter so it reaches
 * the subscription panel regardless of whether it logs headers or query strings —
 * that's the hook a self-hosted panel (3x-ui, Marzban, custom) uses to count how
 * many distinct devices are pulling one subscription key.
 *
 * Real-world testing against a live panel showed the fetch failing intermittently
 * in two distinct ways depending on network path: `UnknownHostException` (operator
 * DNS blocking the domain) and certificate mismatch (a network path substituting a
 * different cert mid-handshake) — and confirmed flaky rather than a hard, permanent
 * block (same domain, same device, sometimes fine). Two mitigations follow from
 * that: retry a few times before giving up, and fall back to resolving the host via
 * DNS-over-HTTPS + connecting to that IP directly when plain DNS is what failed.
 * Neither can do anything about a network path actively substituting a wrong
 * certificate mid-handshake — that's not something a client can safely paper over.
 */
class SubscriptionRepository {

    suspend fun fetchServers(subscriptionUrl: String, deviceId: String): Result<List<VlessConfig>> =
        withContext(Dispatchers.IO) {
            var lastResult: Result<List<VlessConfig>> = Result.failure(IllegalStateException("no attempt made"))

            repeat(MAX_ATTEMPTS) { attempt ->
                lastResult = runCatching { fetchOnce(subscriptionUrl, deviceId) }
                if (lastResult.isSuccess) return@withContext lastResult

                val error = lastResult.exceptionOrNull()
                if (error is UnknownHostException) {
                    val viaDoh = runCatching { fetchViaDoh(subscriptionUrl, deviceId) }
                    if (viaDoh.isSuccess) return@withContext viaDoh
                    lastResult = viaDoh
                } else if (error is javax.net.ssl.SSLException) {
                    TlsDiagnostics.logChainForFailedRequest(subscriptionUrl)
                }

                if (attempt < MAX_ATTEMPTS - 1) delay(RETRY_DELAY_MS)
            }

            lastResult
        }

    private fun fetchOnce(subscriptionUrl: String, deviceId: String): List<VlessConfig> {
        val urlWithDevice = appendDeviceId(subscriptionUrl, deviceId)
        val connection = URL(urlWithDevice).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 12_000
        connection.setRequestProperty("hwid", deviceId)
        connection.setRequestProperty("X-Device-Id", deviceId)
        connection.setRequestProperty("User-Agent", "NetBridge/1.0 (device:$deviceId)")

        return try {
            val code = connection.responseCode
            check(code in 200..299) { "HTTP $code" }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            SubscriptionParser.parse(body)
        } finally {
            connection.disconnect()
        }
    }

    /** Resolves [subscriptionUrl]'s host via DoH and connects to that IP directly, SNI/verified against the real hostname. */
    private fun fetchViaDoh(subscriptionUrl: String, deviceId: String): List<VlessConfig> {
        val url = URL(appendDeviceId(subscriptionUrl, deviceId))
        val realHost = url.host
        val ip = DohResolver.resolve(realHost) ?: throw UnknownHostException("DoH could not resolve $realHost either")

        val portSuffix = if (url.port != -1) ":${url.port}" else ""
        val ipUrl = URL("${url.protocol}://$ip$portSuffix${url.file}")
        val connection = ipUrl.openConnection() as HttpsURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 12_000
        connection.sslSocketFactory = SniPinnedSocketFactory(realHost)
        connection.hostnameVerifier = PinnedHostnameVerifier(realHost)
        connection.setRequestProperty("Host", realHost)
        connection.setRequestProperty("hwid", deviceId)
        connection.setRequestProperty("X-Device-Id", deviceId)
        connection.setRequestProperty("User-Agent", "NetBridge/1.0 (device:$deviceId)")

        return try {
            val code = connection.responseCode
            check(code in 200..299) { "HTTP $code (via DoH-resolved $ip)" }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            SubscriptionParser.parse(body)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Query param name matters: the live panel this was tested against silently
     * substitutes a placeholder server unless it recognizes the hwid — `hwid` is
     * what Happ/v2RayTun/V2Box/InCY all send. `device_id` is kept alongside for
     * panels that use that name instead; harmless extra param otherwise.
     */
    private fun appendDeviceId(url: String, deviceId: String): String {
        val separator = if (url.contains("?")) "&" else "?"
        return "$url${separator}hwid=$deviceId&device_id=$deviceId"
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 1200L
    }
}
