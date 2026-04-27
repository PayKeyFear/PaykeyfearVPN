package com.paykeyfear.vpn.service

import com.google.common.truth.Truth.assertThat
import com.paykeyfear.vpn.core.Protector
import com.paykeyfear.vpn.core.VpnTunnel
import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.Endpoint
import com.paykeyfear.vpn.core.model.Protocol
import com.paykeyfear.vpn.core.model.TunnelState
import com.paykeyfear.vpn.core.model.TunnelStats
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class TunnelControllerTest {
    private val awgConfig =
        ConnectionConfig.Awg(
            id = "test",
            displayName = "Test",
            endpoint = Endpoint("1.2.3.4", 51820),
            privateKey = "aa",
            peerPublicKey = "bb",
            addresses = listOf("10.0.0.2/32"),
        )

    @Test
    fun `start dispatches to tunnel for matching protocol and emits Connected`() = runTest {
        val tunnel = mockk<VpnTunnel>(relaxed = true)
        every { tunnel.supportedProtocol } returns Protocol.AWG
        coEvery { tunnel.start(any(), any(), any()) } returns Unit

        val controller = TunnelController(mapOf(Protocol.AWG to tunnel))
        controller.start(awgConfig, tunFd = 42)

        assertThat(controller.state.value).isInstanceOf(TunnelState.Connected::class.java)
        coVerify { tunnel.start(awgConfig, 42, any()) }
    }

    @Test
    fun `stop transitions back to Disconnected`() = runTest {
        val controller = TunnelController(mapOf(Protocol.AWG to stubTunnel()))
        controller.start(awgConfig, 42)
        controller.stop()
        assertThat(controller.state.value).isEqualTo(TunnelState.Disconnected)
    }

    @Test
    fun `start on failing tunnel exposes Error state and rethrows`() = runTest {
        val tunnel = mockk<VpnTunnel>(relaxed = true)
        every { tunnel.supportedProtocol } returns Protocol.AWG
        coEvery { tunnel.start(any(), any(), any()) } throws IllegalStateException("boom")

        val controller = TunnelController(mapOf(Protocol.AWG to tunnel))
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { controller.start(awgConfig, 42) }
        }
        assertThat(controller.state.value).isInstanceOf(TunnelState.Error::class.java)
    }

    @Test
    fun `start without matching protocol throws`() = runTest {
        val controller = TunnelController(emptyMap())
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { controller.start(awgConfig, 0) }
        }
    }

    @Test
    fun `start twice auto-stops the prior tunnel`() = runTest {
        val tunnel = mockk<VpnTunnel>(relaxed = true)
        every { tunnel.supportedProtocol } returns Protocol.AWG
        coEvery { tunnel.start(any(), any(), any()) } returns Unit

        val controller = TunnelController(mapOf(Protocol.AWG to tunnel))
        controller.start(awgConfig, 1)
        controller.start(awgConfig, 2)

        assertThat(controller.state.value).isInstanceOf(TunnelState.Connected::class.java)
        // Prior tunnel was stopped once (auto-stop) before the second start.
        coVerify(exactly = 1) { tunnel.stop() }
        coVerify(exactly = 2) { tunnel.start(any(), any(), any()) }
    }

    @Test
    fun `stop then start re-establishes tunnel (reconnect path)`() = runTest {
        val tunnel = stubTunnel()
        val controller = TunnelController(mapOf(Protocol.AWG to tunnel))
        controller.start(awgConfig, 1)
        controller.stop()
        // Same controller, second start with same config — mirrors what the
        // PaykeyfearVpnService.networkCallback does on Wi-Fi ⇄ cellular.
        controller.start(awgConfig, 2)
        assertThat(controller.state.value).isInstanceOf(TunnelState.Connected::class.java)
    }

    private fun stubTunnel(): VpnTunnel = object : VpnTunnel {
        override val supportedProtocol: Protocol = Protocol.AWG

        override suspend fun start(config: ConnectionConfig, tunFd: Int, protector: Protector, ruBypassEnabled: Boolean) = Unit

        override suspend fun stop() = Unit

        override fun stats(): Flow<TunnelStats> = flowOf(TunnelStats.ZERO)
    }
}
