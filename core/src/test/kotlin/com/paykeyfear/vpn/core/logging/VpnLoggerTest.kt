package com.paykeyfear.vpn.core.logging

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import timber.log.Timber

class VpnLoggerTest {
    @Test
    fun `install plants ring buffer exactly once and captures messages`() {
        // Clean up any residual trees from other tests in the same JVM.
        Timber.uprootAll()
        VpnLogger.install(debug = false)
        VpnLogger.install(debug = false) // should be idempotent
        VpnLogger.clear()

        Timber.tag("UnitTest").i("hello %s", "world")
        Timber.tag("UnitTest").w("careful")

        val entries = VpnLogger.snapshot()
        assertThat(entries).hasSize(2)
        assertThat(entries[0].message).isEqualTo("hello world")
        assertThat(entries[0].tag).isEqualTo("UnitTest")
        assertThat(entries[1].message).isEqualTo("careful")

        val ringTrees = Timber.forest().filterIsInstance<RingBufferTree>()
        assertThat(ringTrees).hasSize(1)
        Timber.uprootAll()
    }

    @Test
    fun `ring buffer discards oldest entries beyond capacity`() {
        val tree = RingBufferTree(capacity = 3)
        tree.log(4, "T", "a", null)
        tree.log(4, "T", "b", null)
        tree.log(4, "T", "c", null)
        tree.log(4, "T", "d", null)
        val msgs = tree.entries.value.map { it.message }
        assertThat(msgs).containsExactly("b", "c", "d").inOrder()
    }

    @Test
    fun `clear empties the buffer`() {
        val tree = RingBufferTree(capacity = 5)
        tree.log(4, "T", "x", null)
        tree.clear()
        assertThat(tree.entries.value).isEmpty()
    }
}
