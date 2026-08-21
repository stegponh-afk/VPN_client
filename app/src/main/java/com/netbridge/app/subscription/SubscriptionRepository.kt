package com.netbridge.app.subscription

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.net.UnknownHostException
import javax.net.ssl.HttpsURLConnection

/**
 * Fetches the subscription content over HTTPS and hands it to [SubscriptionParser],
 * plus reads the subscription metadata a panel sends alongside it as response
 * headers (`profile-title`, `announce`, `subscription-userinfo`, etc. — the
 * de-facto standard used by Xray/sing-box/Clash-compatible subscription APIs,
 * confirmed against a live Remnawave panel).
 *
 * The device id is sent as the `X-HWID` header (confirmed against a live
 * Remnawave panel via a real client's captured traffic — see [appendDeviceId]),
 * plus query params kept alongside for panels that use those instead — that's
 * the hook a self-hosted panel uses to count how many distinct devices are
 * pulling one subscription key.
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

    suspend fun fetchServers(subscriptionUrl: String, deviceId: String): Result<SubscriptionFetchResult> =
        withContext(Dispatchers.IO) {
            var lastResult: Result<SubscriptionFetchResult> = Result.failure(IllegalStateException("no attempt made"))

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

    private fun fetchOnce(subscriptionUrl: String, deviceId: String): SubscriptionFetchResult {
        val urlWithDevice = appendDeviceId(subscriptionUrl, deviceId)
        val connection = URL(urlWithDevice).openConnection() as HttpURLConnection
        connection.connectTimeout = 12_000
        connection.readTimeout = 12_000
        connection.setRequestProperty("X-HWID", deviceId)
        connection.setRequestProperty("X-Device-Id", deviceId)
        connection.setRequestProperty("User-Agent", "NetBridge/1.0 (device:$deviceId)")

        return try {
            val code = connection.responseCode
            check(code in 200..299) { "HTTP $code" }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            SubscriptionFetchResult(SubscriptionParser.parse(body), parseSubscriptionInfo(connection))
        } finally {
            connection.disconnect()
        }
    }

    /** Resolves [subscriptionUrl]'s host via DoH and connects to that IP directly, SNI/verified against the real hostname. */
    private fun fetchViaDoh(subscriptionUrl: String, deviceId: String): SubscriptionFetchResult {
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
        connection.setRequestProperty("X-HWID", deviceId)
        connection.setRequestProperty("X-Device-Id", deviceId)
        connection.setRequestProperty("User-Agent", "NetBridge/1.0 (device:$deviceId)")

        return try {
            val code = connection.responseCode
            check(code in 200..299) { "HTTP $code (via DoH-resolved $ip)" }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            SubscriptionFetchResult(SubscriptionParser.parse(body), parseSubscriptionInfo(connection))
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSubscriptionInfo(connection: URLConnection): SubscriptionInfo? {
        val title = decodeHeader(connection.getHeaderField("profile-title"))
        val announce = decodeHeader(connection.getHeaderField("announce"))
        val supportUrl = connection.getHeaderField("support-url")
        val updateInterval = connection.getHeaderField("profile-update-interval")?.toIntOrNull()
        val userinfo = parseUserinfo(connection.getHeaderField("subscription-userinfo"))

        if (title == null && announce == null && userinfo == null) return null
        return SubscriptionInfo(
            title = title,
            announce = announce,
            supportUrl = supportUrl,
            uploadBytes = userinfo?.get("upload"),
            downloadBytes = userinfo?.get("download"),
            totalBytes = userinfo?.get("total"),
            expireEpochSeconds = userinfo?.get("expire"),
            updateIntervalHours = updateInterval,
        )
    }

    /** Handles the `base64:<payload>` convention these headers use, falling back to the raw value. */
    private fun decodeHeader(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val payload = value.removePrefix("base64:")
        return runCatching { String(Base64.decode(payload, Base64.DEFAULT), Charsets.UTF_8) }.getOrNull() ?: value
    }

    private fun parseUserinfo(value: String?): Map<String, Long>? {
        if (value.isNullOrBlank()) return null
        return value.split(";")
            .mapNotNull { part ->
                val (key, v) = part.trim().split("=", limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
                key to (v.toLongOrNull() ?: return@mapNotNull null)
            }
            .toMap()
            .takeIf { it.isNotEmpty() }
    }

    /**
     * The panel this was tested against (Remnawave) only checks the `X-HWID`
     * header — confirmed by capturing Happ's actual request via PCAPdroid TLS
     * decryption. Query params and User-Agent turned out not to matter at all;
     * plenty of guessing (recognized-client User-Agent strings, `hwid`/`device_id`
     * query params) failed until the header name was known for certain. Query
     * params are kept as harmless extras for panels that read those instead.
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
