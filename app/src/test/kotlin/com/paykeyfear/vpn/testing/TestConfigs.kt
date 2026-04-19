package com.paykeyfear.vpn.testing

import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.Endpoint

fun testVlessConfig(
    id: String = "vless-1",
    displayName: String = "Test VLESS",
    host: String = "example.com",
    port: Int = 443,
): ConnectionConfig.Vless =
    ConnectionConfig.Vless(
        id = id,
        displayName = displayName,
        endpoint = Endpoint(host, port),
        userId = "00000000-0000-0000-0000-000000000000",
    )
