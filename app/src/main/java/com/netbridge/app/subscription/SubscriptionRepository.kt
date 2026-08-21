package com.netbridge.app.subscription

import com.netbridge.app.model.VlessConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches the subscription content over HTTPS and hands it to [SubscriptionParser].
 *
 * The device id is sent both as a header and as a query parameter so it reaches
 * the subscription panel regardless of whether it logs headers or query strings —
 * that's the hook a self-hosted panel (3x-ui, Marzban, custom) uses to count how
 * many distinct devices are pulling one subscription key.
 */
class SubscriptionRepository {

    suspend fun fetchServers(subscriptionUrl: String, deviceId: String): Result<List<VlessConfig>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val urlWithDevice = appendDeviceId(subscriptionUrl, deviceId)
                val connection = URL(urlWithDevice).openConnection() as HttpURLConnection
                connection.connectTimeout = 15_000
                connection.readTimeout = 15_000
                connection.setRequestProperty("X-Device-Id", deviceId)
                connection.setRequestProperty("User-Agent", "NetBridge/1.0 (device:$deviceId)")

                try {
                    val code = connection.responseCode
                    check(code in 200..299) { "HTTP $code" }
                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    SubscriptionParser.parse(body)
                } finally {
                    connection.disconnect()
                }
            }.onFailure { error ->
                if (error is javax.net.ssl.SSLException || error.cause is javax.net.ssl.SSLException) {
                    TlsDiagnostics.logChainForFailedRequest(subscriptionUrl)
                }
            }
        }

    private fun appendDeviceId(url: String, deviceId: String): String {
        val separator = if (url.contains("?")) "&" else "?"
        return "$url${separator}device_id=$deviceId"
    }
}
