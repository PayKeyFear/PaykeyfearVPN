package com.paykeyfear.vpn.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.paykeyfear.vpn.testing.FakePreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `state mirrors preferences`() = runTest(dispatcher) {
        val prefs = FakePreferencesRepository(dynamicColor = false, connectOnBoot = true)
        val vm = SettingsViewModel(prefs)
        vm.state.test {
            var s = awaitItem()
            while (s.dynamicColorEnabled || !s.connectOnBoot) s = awaitItem()
            assertThat(s.dynamicColorEnabled).isFalse()
            assertThat(s.connectOnBoot).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setDynamicColor and setConnectOnBoot propagate to preferences`() = runTest(dispatcher) {
        val prefs = FakePreferencesRepository()
        val vm = SettingsViewModel(prefs)
        vm.setDynamicColor(false)
        vm.setConnectOnBoot(true)
        dispatcher.scheduler.advanceUntilIdle()
        prefs.dynamicColorEnabled.test {
            assertThat(awaitItem()).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
        prefs.connectOnBoot.test {
            assertThat(awaitItem()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
