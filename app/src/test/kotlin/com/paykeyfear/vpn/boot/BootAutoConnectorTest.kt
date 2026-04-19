package com.paykeyfear.vpn.boot

import com.google.common.truth.Truth.assertThat
import com.paykeyfear.vpn.testing.FakeConfigRepository
import com.paykeyfear.vpn.testing.FakePreferencesRepository
import com.paykeyfear.vpn.testing.testVlessConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BootAutoConnectorTest {
    @Test
    fun `returns null when connectOnBoot is disabled`() = runTest {
        val cfg = testVlessConfig(id = "a")
        val prefs = FakePreferencesRepository(selectedId = "a", connectOnBoot = false)
        val repo = FakeConfigRepository(listOf(cfg))
        assertThat(BootAutoConnector.resolvePendingConfig(prefs, repo)).isNull()
    }

    @Test
    fun `returns null when no config is selected`() = runTest {
        val prefs = FakePreferencesRepository(selectedId = null, connectOnBoot = true)
        val repo = FakeConfigRepository(listOf(testVlessConfig(id = "a")))
        assertThat(BootAutoConnector.resolvePendingConfig(prefs, repo)).isNull()
    }

    @Test
    fun `returns null when selected config no longer exists`() = runTest {
        val prefs = FakePreferencesRepository(selectedId = "missing", connectOnBoot = true)
        val repo = FakeConfigRepository(listOf(testVlessConfig(id = "other")))
        assertThat(BootAutoConnector.resolvePendingConfig(prefs, repo)).isNull()
    }

    @Test
    fun `returns selected config when all conditions are met`() = runTest {
        val cfg = testVlessConfig(id = "a")
        val prefs = FakePreferencesRepository(selectedId = "a", connectOnBoot = true)
        val repo = FakeConfigRepository(listOf(cfg))
        assertThat(BootAutoConnector.resolvePendingConfig(prefs, repo)).isEqualTo(cfg)
    }
}
