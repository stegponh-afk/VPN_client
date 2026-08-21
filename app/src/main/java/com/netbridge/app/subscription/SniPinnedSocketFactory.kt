package com.netbridge.app.subscription

import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

/**
 * SSLSocketFactory that connects to whatever IP the underlying [Socket] is already
 * bound to, but performs the TLS handshake (SNI extension + hostname verification)
 * against [sniHost] instead of that IP. Standard "connect by IP, verify by name"
 * pattern — does not weaken certificate validation, it just decouples which DNS
 * result picked the IP from which name the cert is checked against.
 */
class SniPinnedSocketFactory(private val sniHost: String) : SSLSocketFactory() {

    private val delegate: SSLSocketFactory = getDefault() as SSLSocketFactory

    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket =
        delegate.createSocket(s, sniHost, port, autoClose)

    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites
    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

    override fun createSocket(host: String, port: Int): Socket = delegate.createSocket(host, port)
    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
        delegate.createSocket(host, port, localHost, localPort)
    override fun createSocket(host: InetAddress, port: Int): Socket = delegate.createSocket(host, port)
    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
        delegate.createSocket(address, port, localAddress, localPort)
}
