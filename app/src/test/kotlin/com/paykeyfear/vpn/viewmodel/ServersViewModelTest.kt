package com.paykeyfear.vpn.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
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
class ServersViewModelTest {
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
    fun `observes repository contents alongside selected id`() = runTest(dispatcher) {
        val cfg = testVlessConfig(id = "s1", displayName = "Server 1")
        val repo = FakeConfigRepository(listOf(cfg))
        val prefs = FakePreferencesRepository(selectedId = "s1")
        val vm = ServersViewModel(repo, prefs)
        vm.state.test {
            var snapshot = awaitItem()
            while (snapshot.servers.isEmpty()) snapshot = awaitItem()
            assertThat(snapshot.servers.single().id).isEqualTo("s1")
            assertThat(snapshot.selectedId).isEqualTo("s1")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `select updates preferences`() = runTest(dispatcher) {
        val cfg = testVlessConfig(id = "s1")
        val prefs = FakePreferencesRepository()
        val vm = ServersViewModel(FakeConfigRepository(listOf(cfg)), prefs)
        vm.select("s1")
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(prefs.currentSelectedId()).isEqualTo("s1")
    }

    @Test
    fun `delete removes entry and clears selection if it was selected`() = runTest(dispatcher) {
        val cfg = testVlessConfig(id = "s1")
        val repo = FakeConfigRepository(listOf(cfg))
        val prefs = FakePreferencesRepository(selectedId = "s1")
        val vm = ServersViewModel(repo, prefs)
        vm.state.test {
            var snapshot = awaitItem()
            while (snapshot.servers.isEmpty()) snapshot = awaitItem()
            vm.delete("s1")
            dispatcher.scheduler.advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
        repo.observeAll().test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(prefs.currentSelectedId()).isNull()
    }

    @Test
    fun `delete leaves selection untouched for a non-selected entry`() = runTest(dispatcher) {
        val keep = testVlessConfig(id = "keep")
        val other = testVlessConfig(id = "other")
        val repo = FakeConfigRepository(listOf(keep, other))
        val prefs = FakePreferencesRepository(selectedId = "keep")
        val vm = ServersViewModel(repo, prefs)
        vm.state.test {
            var snapshot = awaitItem()
            while (snapshot.servers.size < 2) snapshot = awaitItem()
            vm.delete("other")
            dispatcher.scheduler.advanceUntilIdle()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(prefs.currentSelectedId()).isEqualTo("keep")
    }
}
