package com.paykeyfear.vpn.core.util

import android.util.Base64

object Base64Keys {
    private const val CURVE25519_LEN = 32

    fun isValidWgKey(value: String): Boolean {
        if (value.length != EXPECTED_LEN) return false
        return runCatching { Base64.decode(value, Base64.NO_WRAP).size == CURVE25519_LEN }.getOrDefault(false)
    }

    private const val EXPECTED_LEN = 44
}
