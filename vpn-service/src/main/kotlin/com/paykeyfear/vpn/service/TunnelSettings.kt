package com.paykeyfear.vpn.service

import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.SplitTunnelMode
import com.paykeyfear.vpn.geo.GeoCidr

/**
 * Snapshot of settings the VPN service needs when establishing a tunnel.
 * Implemented by :app (backed by DataStore) and handed to the service via
 * Hilt, so :vpn-service doesn't take a dependency on :app.
 */
interface TunnelSettings {
    suspend fun splitTunnel(): SplitTunnelConfig

    /** Geo-based route split: whether RU-bound traffic should skip the tunnel. */
    suspend fun ruBypass(): RuBypassConfig

    /**
     * Resolves the config that should be used when the system starts the
     * VpnService itself (e.g. Always-on VPN or Boot auto-connect), with no
     * config embedded in the start intent. Returns null if no server is
     * selected or the selection no longer exists.
     */
    suspend fun selectedConfig(): ConnectionConfig?
}

data class SplitTunnelConfig(
    val mode: SplitTunnelMode,
    val packages: Set<String>,
) {
    companion object {
        val OFF = SplitTunnelConfig(SplitTunnelMode.Off, emptySet())
    }
}

data class RuBypassConfig(
    val enabled: Boolean,
    val ipv4: List<GeoCidr>,
    val ipv6: List<GeoCidr>,
) {
    companion object {
        val OFF = RuBypassConfig(enabled = false, ipv4 = emptyList(), ipv6 = emptyList())
    }
}
