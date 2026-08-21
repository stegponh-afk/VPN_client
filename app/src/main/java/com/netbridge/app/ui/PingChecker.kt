package com.netbridge.app.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Measures raw TCP connect time to a server's address:port — a reasonable proxy
 * for latency without needing the (not-yet-built) tunnel engine to actually be
 * running. Not a real ping (ICMP), just how long the handshake to that port takes.
 */
object PingChecker {

    suspend fun measure(address: String, port: Int, timeoutMs: Int = 3000): Int? =
        withContext(Dispatchers.IO) {
            runCatching {
                val start = System.currentTimeMillis()
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(address, port), timeoutMs)
                }
                (System.currentTimeMillis() - start).toInt()
            }.getOrNull()
        }
}
