package com.paykeyfear.vpn.service

import com.paykeyfear.vpn.core.Protector
import com.paykeyfear.vpn.core.VpnTunnel
import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.Protocol
import com.paykeyfear.vpn.core.model.TunnelState
import com.paykeyfear.vpn.core.model.TunnelStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Protocol-agnostic coordinator: given a [ConnectionConfig], selects the
 * appropriate [VpnTunnel] implementation and drives it through start/stop.
 *
 * The caller (a [PaykeyfearVpnService] on Android, or a test harness on JVM)
 * is responsible for supplying the tun file descriptor.
 */
class TunnelController(
    private val tunnels: Map<Protocol, VpnTunnel>,
    private val statsPollIntervalMs: Long = DEFAULT_STATS_INTERVAL_MS,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<TunnelState>(TunnelState.Disconnected)
    val state: StateFlow<TunnelState> = _state.asStateFlow()

    private var active: VpnTunnel? = null

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val stats: Flow<TunnelStats> =
        _state.flatMapLatest { s ->
            val a = active
            if (s is TunnelState.Connected && a != null) pollStats(a) else flowOf(TunnelStats.ZERO)
        }

    /**
     * Samples [VpnTunnel.stats] on a fixed cadence while the tunnel is active.
     * Each protocol's `stats()` is modelled as a "snapshot flow" that emits
     * once; we re-subscribe every [statsPollIntervalMs] so the UI and
     * foreground notification can display live throughput without each
     * tunnel implementation having to own a ticker.
     */
    private fun pollStats(tunnel: VpnTunnel): Flow<TunnelStats> = flow {
        while (true) {
            tunnel.stats().collect { emit(it) }
            kotlinx.coroutines.delay(statsPollIntervalMs)
        }
    }

    suspend fun start(config: ConnectionConfig, tunFd: Int, protector: Protector = Protector.NOOP) {
        mutex.withLock {
            check(active == null) { "Tunnel already active; stop() first" }
            val tunnel = tunnels[config.protocol]
                ?: error("No tunnel implementation for protocol ${config.protocol}")
            _state.value = TunnelState.Connecting
            try {
                tunnel.start(config, tunFd, protector)
                active = tunnel
                _state.value =
                    TunnelState.Connected(
                        configId = config.id,
                        protocol = config.protocol,
                        connectedAtEpochMs = System.currentTimeMillis(),
                    )
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "Tunnel start failed for ${config.protocol}")
                _state.value = TunnelState.Error(t.message ?: "Unknown tunnel error", t)
                runCatching { tunnel.stop() }
                throw t
            }
        }
    }

    suspend fun stop() {
        mutex.withLock {
            val current = active ?: return
            _state.value = TunnelState.Disconnecting
            runCatching { current.stop() }.onFailure {
                Timber.tag(TAG).w(it, "Error while stopping tunnel")
            }
            active = null
            _state.value = TunnelState.Disconnected
        }
    }

    private companion object {
        const val TAG = "TunnelController"
        const val DEFAULT_STATS_INTERVAL_MS: Long = 1_500
    }
}
