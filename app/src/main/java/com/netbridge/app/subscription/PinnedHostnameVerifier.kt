package com.netbridge.app.subscription

import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSession

/**
 * Companion to [SniPinnedSocketFactory]: when connecting to a URL built from a
 * bare IP literal, `HttpsURLConnection`'s default hostname check compares the
 * cert against that IP string and always fails. This verifies against the real
 * intended hostname instead — still full certificate hostname validation, just
 * against the name the IP was resolved for rather than the IP itself.
 */
class PinnedHostnameVerifier(private val realHost: String) : HostnameVerifier {
    override fun verify(hostname: String?, session: SSLSession?): Boolean =
        HttpsURLConnection.getDefaultHostnameVerifier().verify(realHost, session)
}
