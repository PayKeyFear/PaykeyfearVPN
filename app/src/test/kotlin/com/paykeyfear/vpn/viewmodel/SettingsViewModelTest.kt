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
        val prefs = FakePreferencesRepository(connectOnBoot = true)
        val vm = SettingsViewModel(prefs)
        vm.state.test {
            var s = awaitItem()
            while (!s.connectOnBoot) s = awaitItem()
            assertThat(s.connectOnBoot).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setConnectOnBoot propagates to preferences`() = runTest(dispatcher) {
        val prefs = FakePreferencesRepository()
        val vm = SettingsViewModel(prefs)
        vm.setConnectOnBoot(true)
        dispatcher.scheduler.advanceUntilIdle()
        prefs.connectOnBoot.test {
            assertThat(awaitItem()).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
