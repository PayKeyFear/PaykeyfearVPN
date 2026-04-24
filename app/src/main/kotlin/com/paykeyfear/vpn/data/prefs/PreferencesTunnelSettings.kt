package com.paykeyfear.vpn.data.prefs

import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.data.repository.ConfigRepository
import com.paykeyfear.vpn.geo.GeoCidrParser
import com.paykeyfear.vpn.geo.GeoRepository
import com.paykeyfear.vpn.service.RuBypassConfig
import com.paykeyfear.vpn.service.SplitTunnelConfig
import com.paykeyfear.vpn.service.TunnelSettings
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class PreferencesTunnelSettings
    @Inject
    constructor(
        private val preferences: PreferencesRepository,
        private val configs: ConfigRepository,
        private val geo: GeoRepository,
    ) : TunnelSettings {
        override suspend fun splitTunnel(): SplitTunnelConfig =
            SplitTunnelConfig(
                mode = preferences.splitTunnelMode.first(),
                packages = preferences.splitTunnelPackages.first(),
            )

        override suspend fun ruBypass(): RuBypassConfig {
            val enabled = preferences.ruBypassEnabled.first()
            if (!enabled) return RuBypassConfig.OFF
            val cidrs = GeoCidrParser.parse(geo.readRuCidr())
            val (v6, v4) = cidrs.partition { it.isIpv6 }
            return RuBypassConfig(enabled = true, ipv4 = v4, ipv6 = v6)
        }

        override suspend fun selectedConfig(): ConnectionConfig? {
            val id = preferences.selectedConfigId.first() ?: return null
            return configs.findById(id)
        }
    }
