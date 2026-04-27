package com.paykeyfear.vpn.core

import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.TunnelStats
import kotlinx.coroutines.flow.Flow

/**
 * Android's `VpnService.protect(int)` exposed as a tiny functional interface so
 * non-Android code (and unit tests) doesn't need to depend on the Android SDK.
 * Returning `true` means the socket has been excluded from the VPN route and
 * will reach the real network; `false` means the caller should treat the
 * socket as unsafe to use for the protocol's own egress traffic.
 */
fun interface Protector {
    fun protect(socketFd: Int): Boolean

    companion object {
        /** Default no-op for unit tests and the noop protocol paths. */
        val NOOP: Protector = Protector { true }
    }
}

/**
 * Abstraction over a protocol-specific tunnel implementation.
 *
 * Each supported protocol (AWG, VLESS, Hysteria2) provides its own adapter
 * that translates a [ConnectionConfig] into the underlying native backend.
 *
 * [protector] must be threaded into every userspace socket the protocol
 * opens towards the real internet — otherwise traffic loops back through the
 * VPN and the tunnel deadlocks on first handshake.
 */
interface VpnTunnel {
    val supportedProtocol: com.paykeyfear.vpn.core.model.Protocol

    suspend fun start(config: ConnectionConfig, tunFd: Int, protector: Protector, ruBypassEnabled: Boolean = false)

    suspend fun stop()

    fun stats(): Flow<TunnelStats>
}
