package com.paykeyfear.vpn.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.paykeyfear.vpn.core.model.Protocol
import com.paykeyfear.vpn.core.model.TunnelState
import com.paykeyfear.vpn.service.TunnelController
import com.paykeyfear.vpn.testing.FakeConfigRepository
import com.paykeyfear.vpn.testing.FakePreferencesRepository
import com.paykeyfear.vpn.testing.testVlessConfig
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
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun controller(): TunnelController = TunnelController(emptyMap())

    @Test
    fun `initial state is disconnected`() = runTest(dispatcher) {
        val vm = HomeViewModel(FakeConfigRepository(), controller(), FakePreferencesRepository())
        val s = vm.state.value
        assertThat(s.isConnected).isFalse()
        assertThat(s.tunnelState).isEqualTo(TunnelState.Disconnected)
    }

    @Test
    fun `toggle with no servers does not emit event`() = runTest(dispatcher) {
        val vm = HomeViewModel(FakeConfigRepository(), controller(), FakePreferencesRepository())
        vm.state.test {
            awaitItem()
            vm.toggle()
            dispatcher.scheduler.advanceUntilIdle()
            assertThat(vm.events.value).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `toggle with selected config emits RequestVpnPermission event`() = runTest(dispatcher) {
        val cfg = testVlessConfig()
        val repo = FakeConfigRepository(listOf(cfg))
        val prefs = FakePreferencesRepository(selectedId = cfg.id)
        val vm = HomeViewModel(repo, controller(), prefs)
        vm.state.test {
            var snapshot = awaitItem()
            while (snapshot.selected == null) snapshot = awaitItem()
            assertThat(snapshot.selected?.id).isEqualTo(cfg.id)
            vm.toggle()
            dispatcher.scheduler.advanceUntilIdle()
            val evt = vm.events.value
            assertThat(evt).isInstanceOf(HomeEvent.RequestVpnPermissionThenConnect::class.java)
            assertThat((evt as HomeEvent.RequestVpnPermissionThenConnect).config.id).isEqualTo(cfg.id)
            assertThat(repo.markedUsed.map { it.first }).contains(cfg.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `falls back to first config when selectedId does not match`() = runTest(dispatcher) {
        val cfg = testVlessConfig(id = "first")
        val repo = FakeConfigRepository(listOf(cfg))
        val prefs = FakePreferencesRepository(selectedId = "missing")
        val vm = HomeViewModel(repo, controller(), prefs)
        vm.state.test {
            var snapshot = awaitItem()
            while (snapshot.selected == null) snapshot = awaitItem()
            assertThat(snapshot.selected?.id).isEqualTo("first")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `consumeEvent clears pending event`() = runTest(dispatcher) {
        val cfg = testVlessConfig()
        val vm = HomeViewModel(
            FakeConfigRepository(listOf(cfg)),
            controller(),
            FakePreferencesRepository(selectedId = cfg.id),
        )
        vm.state.test {
            var snapshot = awaitItem()
            while (snapshot.selected == null) snapshot = awaitItem()
            vm.toggle()
            dispatcher.scheduler.advanceUntilIdle()
            assertThat(vm.events.value).isNotNull()
            vm.consumeEvent()
            assertThat(vm.events.value).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `statusLabel reflects connected protocol name`() {
        val s = HomeUiState(
            TunnelState.Connected(
                configId = "c",
                protocol = Protocol.AWG,
                connectedAtEpochMs = 0L,
            ),
        )
        assertThat(s.isConnected).isTrue()
        assertThat(s.statusLabel).contains("AmneziaWG")
    }
}
