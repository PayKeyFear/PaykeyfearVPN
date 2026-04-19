package com.paykeyfear.vpn.service

import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.SplitTunnelMode

/**
 * Snapshot of settings the VPN service needs when establishing a tunnel.
 * Implemented by :app (backed by DataStore) and handed to the service via
 * Hilt, so :vpn-service doesn't take a dependency on :app.
 */
interface TunnelSettings {
    suspend fun splitTunnel(): SplitTunnelConfig

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
