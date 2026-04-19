package com.paykeyfear.vpn.core.model

/**
 * Controls how the VPN tunnel interacts with installed apps.
 *
 * - [Off]: every app routes through the tunnel (default).
 * - [Allowlist]: only the chosen packages route through the tunnel; everything
 *   else uses the regular network.
 * - [Denylist]: the chosen packages bypass the tunnel; everything else routes
 *   through it.
 */
enum class SplitTunnelMode {
    Off,
    Allowlist,
    Denylist,
    ;

    companion object {
        fun fromStorageValue(raw: String?): SplitTunnelMode =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: Off
    }
}
