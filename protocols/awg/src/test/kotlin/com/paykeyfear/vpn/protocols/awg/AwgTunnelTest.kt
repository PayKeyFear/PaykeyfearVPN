package com.paykeyfear.vpn.protocols.awg

import com.google.common.truth.Truth.assertThat
import com.paykeyfear.vpn.core.Protector
import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.Endpoint
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test

class AwgTunnelTest {
    private val cfg = ConnectionConfig.Awg(
        id = "id",
        displayName = "n",
        endpoint = Endpoint("h", 51820),
        privateKey = "PRIV",
        peerPublicKey = "PUB",
        addresses = listOf("10.0.0.2/32"),
    )

    @Test
    fun `start with available native calls start with rendered config`() = runTest {
        val native = mockk<AwgTunnel.Native>(relaxed = true)
        every { native.available() } returns true
        every { native.start(any(), any()) } returns 9000L

        val tunnel = AwgTunnel(native = native)
        tunnel.start(cfg, tunFd = 7, Protector.NOOP)

        verify {
            native.installProtector(Protector.NOOP)
            native.start(
                match { it.contains("private_key=PRIV") && it.contains("endpoint=h:51820") },
                7,
            )
        }
    }

    @Test
    fun `start with unavailable native falls back to fake handle`() = runTest {
        val native = mockk<AwgTunnel.Native>(relaxed = true)
        every { native.available() } returns false

        val tunnel = AwgTunnel(native = native)
        tunnel.start(cfg, tunFd = 7, Protector.NOOP)
        // No crash; stop should be a noop.
        tunnel.stop()
    }

    @Test
    fun `start twice throws`() = runTest {
        val native = mockk<AwgTunnel.Native>(relaxed = true)
        every { native.available() } returns true
        every { native.start(any(), any()) } returns 1L

        val tunnel = AwgTunnel(native = native)
        tunnel.start(cfg, 1, Protector.NOOP)
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { tunnel.start(cfg, 1, Protector.NOOP) }
        }
    }

    @Test
    fun `start rejects non-AWG config`() = runTest {
        val tunnel = AwgTunnel()
        val wrong = ConnectionConfig.Vless(
            id = "i",
            displayName = "n",
            endpoint = Endpoint("h", 443),
            userId = "u",
        )
        assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.runBlocking { tunnel.start(wrong, 1, Protector.NOOP) }
        }
    }

    @Test
    fun `start returning invalid handle fails`() = runTest {
        val native = mockk<AwgTunnel.Native>(relaxed = true)
        every { native.available() } returns true
        every { native.start(any(), any()) } returns AwgTunnel.INVALID_HANDLE

        val tunnel = AwgTunnel(native = native)
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { tunnel.start(cfg, 1, Protector.NOOP) }
        }
        assertThat(Unit).isEqualTo(Unit)
    }
}
