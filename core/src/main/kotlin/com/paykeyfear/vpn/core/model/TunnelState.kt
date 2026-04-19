package com.paykeyfear.vpn.core.model

sealed interface TunnelState {
    data object Disconnected : TunnelState

    data object Connecting : TunnelState

    data class Connected(
        val configId: String,
        val protocol: Protocol,
        val connectedAtEpochMs: Long,
    ) : TunnelState

    data object Disconnecting : TunnelState

    data class Error(val message: String, val cause: Throwable? = null) : TunnelState
}
