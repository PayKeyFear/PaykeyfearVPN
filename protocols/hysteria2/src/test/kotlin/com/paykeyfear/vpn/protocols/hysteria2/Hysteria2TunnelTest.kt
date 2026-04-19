package com.paykeyfear.vpn.protocols.hysteria2

import com.google.common.truth.Truth.assertThat
import com.paykeyfear.vpn.core.Protector
import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.Endpoint
import com.paykeyfear.vpn.core.model.TunnelStats
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class Hysteria2TunnelTest {
    private val cfg = ConnectionConfig.Hysteria2(
        id = "x",
        displayName = "n",
        endpoint = Endpoint("edge", 8443),
        password = "p",
    )

    @Test
    fun `noop adapter transitions start-stop without throwing`() = runTest {
        val tunnel = Hysteria2Tunnel(clock = { 42L })
        tunnel.start(cfg, tunFd = 7, protector = Protector.NOOP)
        val stats = tunnel.stats().first()
        assertThat(stats).isEqualTo(TunnelStats.ZERO.copy(sampledAtEpochMs = 42L))
        tunnel.stop()
    }

    @Test
    fun `double start fails fast`() = runTest {
        val tunnel = Hysteria2Tunnel()
        tunnel.start(cfg, 7, Protector.NOOP)
        try {
            tunnel.start(cfg, 7, Protector.NOOP)
            error("expected IllegalStateException")
        } catch (_: IllegalStateException) {
            // expected
        }
    }

    @Test
    fun `stats from native adapter are surfaced`() = runTest {
        val fake = FakeAdapter(rx = 111L, tx = 222L)
        val tunnel = Hysteria2Tunnel(adapter = fake, clock = { 999L })
        tunnel.start(cfg, tunFd = 1, protector = Protector.NOOP)
        val stats = tunnel.stats().first()
        assertThat(stats).isEqualTo(TunnelStats(rxBytes = 111L, txBytes = 222L, sampledAtEpochMs = 999L))
        assertThat(fake.protectorInstalled).isTrue()
        tunnel.stop()
        assertThat(fake.stopped).isTrue()
    }

    @Test
    fun `stats json parser handles whitespace and missing keys`() {
        assertThat(Hysteria2Native.parseStatsJson("""{"rx": 10, "tx": 20}""")).isEqualTo(longArrayOf(10L, 20L))
        assertThat(Hysteria2Native.parseStatsJson("""{"rx":7}""")).isEqualTo(longArrayOf(7L, 0L))
        assertThat(Hysteria2Native.parseStatsJson("""{}""")).isEqualTo(longArrayOf(0L, 0L))
        assertThat(Hysteria2Native.parseStatsJson("""nonsense""")).isEqualTo(longArrayOf(0L, 0L))
    }

    private class FakeAdapter(
        private val rx: Long,
        private val tx: Long,
    ) : Hysteria2Tunnel.Hysteria2Adapter {
        var protectorInstalled = false
        var stopped = false

        override fun available(): Boolean = true

        override fun start(yaml: String, tunFd: Int, protector: Protector) {
            protectorInstalled = true
        }

        override fun stop() {
            stopped = true
        }

        override fun stats(): LongArray = longArrayOf(rx, tx)
    }
}
