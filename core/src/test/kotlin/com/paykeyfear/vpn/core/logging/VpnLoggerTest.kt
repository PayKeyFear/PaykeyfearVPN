package com.paykeyfear.vpn.core.logging

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import timber.log.Timber

class VpnLoggerTest {
    @After
    fun tearDown() {
        // Each test in this file plants/uproots Timber trees; never leak
        // them across tests in the same JVM (Gradle reuses the daemon).
        Timber.uprootAll()
    }

    @Test
    fun `install plants ring buffer exactly once and captures messages`() {
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
    }

    @Test
    fun `ring buffer discards oldest entries beyond capacity`() {
        // Drive the tree through Timber's public API rather than calling
        // its protected `log` method directly. Some Timber versions wrap
        // the protected leaf with `prepareLog`, which may or may not
        // forward depending on internal state — going through Timber.tag
        // exercises the real production path.
        Timber.uprootAll()
        val tree = RingBufferTree(capacity = 3)
        Timber.plant(tree)

        Timber.tag("T").i("a")
        Timber.tag("T").i("b")
        Timber.tag("T").i("c")
        Timber.tag("T").i("d")

        val msgs = tree.entries.value.map { it.message }
        assertThat(msgs).containsExactly("b", "c", "d").inOrder()
    }

    @Test
    fun `clear empties the buffer`() {
        Timber.uprootAll()
        val tree = RingBufferTree(capacity = 5)
        Timber.plant(tree)

        Timber.tag("T").i("x")
        assertThat(tree.entries.value).hasSize(1)

        tree.clear()
        assertThat(tree.entries.value).isEmpty()
    }
}
