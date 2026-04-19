package com.paykeyfear.vpn.di

import com.paykeyfear.vpn.config.ConfigParserRegistry
import com.paykeyfear.vpn.core.VpnTunnel
import com.paykeyfear.vpn.core.model.Protocol
import com.paykeyfear.vpn.protocols.awg.AwgTunnel
import com.paykeyfear.vpn.protocols.hysteria2.Hysteria2Tunnel
import com.paykeyfear.vpn.protocols.hysteria2.NativeHysteria2Adapter
import com.paykeyfear.vpn.protocols.vless.NativeTun2SocksBridge
import com.paykeyfear.vpn.protocols.vless.NativeXrayAdapter
import com.paykeyfear.vpn.protocols.vless.VlessTunnel
import com.paykeyfear.vpn.service.TunnelController
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideConfigRegistry(): ConfigParserRegistry = ConfigParserRegistry()

    @Provides
    @Singleton
    fun provideTunnelMap(): Map<Protocol, @JvmSuppressWildcards VpnTunnel> =
        mapOf(
            Protocol.AWG to AwgTunnel(),
            Protocol.VLESS to VlessTunnel(
                adapter = NativeXrayAdapter(),
                tun2socks = NativeTun2SocksBridge(),
            ),
            Protocol.HYSTERIA2 to Hysteria2Tunnel(adapter = NativeHysteria2Adapter()),
        )

    @Provides
    @Singleton
    fun provideTunnelController(
        tunnels: Map<Protocol, @JvmSuppressWildcards VpnTunnel>,
    ): TunnelController = TunnelController(tunnels)
}
