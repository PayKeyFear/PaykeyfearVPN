package com.paykeyfear.vpn.geo

import java.math.BigInteger

/**
 * Computes the complement of a CIDR set — i.e. the set of CIDRs that cover
 * everything in the address space EXCEPT the supplied ranges. Used by
 * callers that route the VPN TUN via `VpnService.Builder.addRoute` and
 * need to keep RU-bound traffic outside the tunnel on SDKs that lack
 * `excludeRoute` (< API 33).
 *
 * The implementation works on packed numeric ranges (IPv4 → `Long`,
 * IPv6 → `BigInteger`), merges overlapping RU ranges, emits the gaps,
 * and decomposes each gap into the minimal set of CIDRs that exactly
 * tiles it (classic range-to-CIDR decomposition).
 */
object RouteExclusion {
    fun complementIpv4(cidrs: Collection<GeoCidr>): List<GeoCidr> {
        val ranges = cidrs.asSequence()
            .filter { !it.isIpv6 }
            .mapNotNull(::toRangeV4)
            .toMutableList()
        if (ranges.isEmpty()) return listOf(GeoCidr("0.0.0.0", 0, false))
        ranges.sortBy { it.first }
        val merged = mergeRanges(ranges)
        val gaps = gaps(merged, 0L, IPV4_MAX)
        return gaps.flatMap { (start, end) -> decomposeV4(start, end) }
    }

    fun complementIpv6(cidrs: Collection<GeoCidr>): List<GeoCidr> {
        val ranges = cidrs.asSequence()
            .filter { it.isIpv6 }
            .mapNotNull(::toRangeV6)
            .toMutableList()
        if (ranges.isEmpty()) return listOf(GeoCidr("::", 0, true))
        ranges.sortBy { it.first }
        val merged = mergeRangesBig(ranges)
        val gaps = gapsBig(merged, BigInteger.ZERO, IPV6_MAX)
        return gaps.flatMap { (start, end) -> decomposeV6(start, end) }
    }

    // ---- IPv4 helpers --------------------------------------------------------

    private fun toRangeV4(cidr: GeoCidr): Pair<Long, Long>? {
        val net = parseIpv4(cidr.address) ?: return null
        val mask = if (cidr.prefixLength == 0) 0L else 0xFFFFFFFFL shl (32 - cidr.prefixLength) and 0xFFFFFFFFL
        val start = net and mask
        val end = start or (0xFFFFFFFFL and mask.inv())
        return start to end
    }

    private fun parseIpv4(addr: String): Long? {
        val parts = addr.split('.')
        if (parts.size != 4) return null
        var result = 0L
        for (p in parts) {
            val n = p.toIntOrNull() ?: return null
            if (n !in 0..255) return null
            result = (result shl 8) or n.toLong()
        }
        return result
    }

    private fun formatIpv4(n: Long): String =
        "${(n ushr 24) and 0xFF}.${(n ushr 16) and 0xFF}.${(n ushr 8) and 0xFF}.${n and 0xFF}"

    private fun mergeRanges(sorted: List<Pair<Long, Long>>): List<Pair<Long, Long>> {
        val out = ArrayList<Pair<Long, Long>>(sorted.size)
        var curStart = sorted[0].first
        var curEnd = sorted[0].second
        for (i in 1 until sorted.size) {
            val (s, e) = sorted[i]
            if (s <= curEnd + 1) {
                if (e > curEnd) curEnd = e
            } else {
                out.add(curStart to curEnd)
                curStart = s; curEnd = e
            }
        }
        out.add(curStart to curEnd)
        return out
    }

    private fun gaps(merged: List<Pair<Long, Long>>, lo: Long, hi: Long): List<Pair<Long, Long>> {
        val out = ArrayList<Pair<Long, Long>>(merged.size + 1)
        var cursor = lo
        for ((s, e) in merged) {
            if (s > cursor) out.add(cursor to (s - 1))
            cursor = maxOf(cursor, e + 1)
            if (cursor > hi) return out
        }
        if (cursor <= hi) out.add(cursor to hi)
        return out
    }

    private fun decomposeV4(start: Long, end: Long): List<GeoCidr> {
        val out = ArrayList<GeoCidr>()
        var s = start
        while (s <= end) {
            val lowestBit = if (s == 0L) 32 else java.lang.Long.numberOfTrailingZeros(s).coerceAtMost(32)
            val remaining = end - s + 1
            val sizeByBits = 1L shl lowestBit
            val size = minOf(sizeByBits, java.lang.Long.highestOneBit(remaining))
            val prefix = 32 - java.lang.Long.numberOfTrailingZeros(size).toInt()
            out.add(GeoCidr(formatIpv4(s), prefix, false))
            s += size
        }
        return out
    }

    // ---- IPv6 helpers --------------------------------------------------------

    private fun toRangeV6(cidr: GeoCidr): Pair<BigInteger, BigInteger>? {
        val net = parseIpv6(cidr.address) ?: return null
        val hostBits = 128 - cidr.prefixLength
        val mask = if (hostBits == 0) IPV6_MAX else IPV6_MAX.shiftLeft(hostBits).and(IPV6_MAX).xor(IPV6_MAX).not().and(IPV6_MAX)
        val start = net.and(mask)
        val end = start.or(IPV6_MAX.subtract(mask))
        return start to end
    }

    private fun parseIpv6(addr: String): BigInteger? {
        val normalized = expandV6(addr) ?: return null
        val groups = normalized.split(':')
        if (groups.size != 8) return null
        var result = BigInteger.ZERO
        for (g in groups) {
            if (g.length > 4) return null
            val n = runCatching { Integer.parseInt(g.ifEmpty { "0" }, 16) }.getOrNull() ?: return null
            result = result.shiftLeft(16).add(BigInteger.valueOf(n.toLong()))
        }
        return result
    }

    private fun expandV6(addr: String): String? {
        val parts = addr.split("::")
        if (parts.size > 2) return null
        return if (parts.size == 1) {
            addr
        } else {
            val left = if (parts[0].isEmpty()) emptyList() else parts[0].split(':')
            val right = if (parts[1].isEmpty()) emptyList() else parts[1].split(':')
            val missing = 8 - left.size - right.size
            if (missing < 0) return null
            (left + List(missing) { "0" } + right).joinToString(":")
        }
    }

    private fun formatV6(n: BigInteger): String {
        val groups = IntArray(8)
        var rem = n
        for (i in 7 downTo 0) {
            groups[i] = rem.and(BigInteger.valueOf(0xFFFF)).toInt()
            rem = rem.shiftRight(16)
        }
        return groups.joinToString(":") { "%x".format(it) }
    }

    private fun mergeRangesBig(sorted: List<Pair<BigInteger, BigInteger>>): List<Pair<BigInteger, BigInteger>> {
        val out = ArrayList<Pair<BigInteger, BigInteger>>(sorted.size)
        var curStart = sorted[0].first
        var curEnd = sorted[0].second
        for (i in 1 until sorted.size) {
            val (s, e) = sorted[i]
            if (s <= curEnd.add(BigInteger.ONE)) {
                if (e > curEnd) curEnd = e
            } else {
                out.add(curStart to curEnd)
                curStart = s; curEnd = e
            }
        }
        out.add(curStart to curEnd)
        return out
    }

    private fun gapsBig(
        merged: List<Pair<BigInteger, BigInteger>>,
        lo: BigInteger,
        hi: BigInteger,
    ): List<Pair<BigInteger, BigInteger>> {
        val out = ArrayList<Pair<BigInteger, BigInteger>>(merged.size + 1)
        var cursor = lo
        for ((s, e) in merged) {
            if (s > cursor) out.add(cursor to s.subtract(BigInteger.ONE))
            cursor = maxOf(cursor, e.add(BigInteger.ONE))
            if (cursor > hi) return out
        }
        if (cursor <= hi) out.add(cursor to hi)
        return out
    }

    private fun decomposeV6(start: BigInteger, end: BigInteger): List<GeoCidr> {
        val out = ArrayList<GeoCidr>()
        var s = start
        while (s <= end) {
            val trailingZeros = if (s == BigInteger.ZERO) 128 else s.lowestSetBit
            val remaining = end.subtract(s).add(BigInteger.ONE)
            val sizeByBits = BigInteger.ONE.shiftLeft(trailingZeros)
            val sizeByRemaining = BigInteger.ONE.shiftLeft(remaining.bitLength() - 1)
            val size = if (sizeByBits <= sizeByRemaining) sizeByBits else sizeByRemaining
            val prefix = 128 - (size.bitLength() - 1)
            out.add(GeoCidr(formatV6(s), prefix, true))
            s = s.add(size)
        }
        return out
    }

    private val IPV6_MAX: BigInteger = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE)
    private const val IPV4_MAX = 0xFFFFFFFFL
}
