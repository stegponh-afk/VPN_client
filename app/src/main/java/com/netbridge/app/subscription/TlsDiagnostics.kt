package com.netbridge.app.subscription

import android.util.Log
import java.net.URL
import java.security.MessageDigest
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Diagnostic-only: on a failed subscription fetch, opens a second connection with a
 * trust-everything manager PURELY to log what certificate chain the device's current
 * network path actually presented (subject/issuer/SHA-256), so we can tell a stale
 * client trust store apart from a network path substituting a different certificate.
 * The captured connection's response body is never used for anything.
 */
object TlsDiagnostics {

    private const val TAG = "NetBridgeTLS"

    fun logChainForFailedRequest(urlStr: String) {
        runCatching {
            val captured = mutableListOf<X509Certificate>()
            val trustAll = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                    chain?.let { captured.addAll(it) }
                }
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
            val context = SSLContext.getInstance("TLS")
            context.init(null, arrayOf<TrustManager>(trustAll), null)

            val connection = URL(urlStr).openConnection() as HttpsURLConnection
            connection.sslSocketFactory = context.socketFactory
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            runCatching { connection.connect() }
            runCatching { connection.disconnect() }

            if (captured.isEmpty()) {
                Log.w(TAG, "diagnostic connection captured no certificates for $urlStr")
                return@runCatching
            }
            captured.forEachIndexed { i, cert ->
                val fp = sha256Hex(cert.encoded)
                Log.w(TAG, "chain[$i] subject=${cert.subjectDN} issuer=${cert.issuerDN} notAfter=${cert.notAfter} sha256=$fp")
            }
        }.onFailure { e ->
            Log.w(TAG, "diagnostic connection itself failed: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(":") { "%02X".format(it) }
    }
}
