package com.paykeyfear.vpn.core.model

data class TunnelStats(
    val rxBytes: Long,
    val txBytes: Long,
    val sampledAtEpochMs: Long,
) {
    companion object {
        val ZERO: TunnelStats = TunnelStats(0, 0, 0)
    }
}
