package com.netbridge.app.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class TunnelState { DISCONNECTED, CONNECTING, CONNECTED }

data class TunnelStatusValue(val state: TunnelState, val error: String? = null)

/** Process-wide tunnel status, updated by [CoreVpnService] and observed by the UI. */
object TunnelStatus {
    private val _status = MutableStateFlow(TunnelStatusValue(TunnelState.DISCONNECTED))
    val status: StateFlow<TunnelStatusValue> = _status

    fun update(state: TunnelState, error: String? = null) {
        _status.value = TunnelStatusValue(state, error)
    }
}
