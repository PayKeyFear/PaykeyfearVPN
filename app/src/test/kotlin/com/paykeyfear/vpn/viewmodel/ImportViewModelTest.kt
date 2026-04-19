package com.paykeyfear.vpn.viewmodel

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.paykeyfear.vpn.config.ConfigParserRegistry
import com.paykeyfear.vpn.testing.FakeConfigRepository
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
class ImportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: FakeConfigRepository
    private lateinit var preferences: FakePreferencesRepository
    private lateinit var vm: ImportViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeConfigRepository()
        preferences = FakePreferencesRepository()
        vm = ImportViewModel(ConfigParserRegistry(), repository, preferences)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `valid vless uri produces importedName and persists to repository`() = runTest(dispatcher) {
        vm.onTextChanged("vless://id@host.example:443?encryption=none#Custom")
        vm.onImportClicked()
        dispatcher.scheduler.advanceUntilIdle()
        vm.state.test {
            val item = awaitItem()
            assertThat(item.importedName).isEqualTo("Custom")
            assertThat(item.error).isNull()
            cancelAndIgnoreRemainingEvents()
        }
        repository.observeAll().test {
            val saved = awaitItem()
            assertThat(saved).hasSize(1)
            assertThat(saved.single().displayName).isEqualTo("Custom")
            assertThat(preferences.currentSelectedId()).isEqualTo(saved.single().id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `garbage input produces error and does not persist`() = runTest(dispatcher) {
        vm.onTextChanged("this is not a config")
        vm.onImportClicked()
        dispatcher.scheduler.advanceUntilIdle()
        vm.state.test {
            val item = awaitItem()
            assertThat(item.error).isNotNull()
            assertThat(item.importedName).isNull()
            cancelAndIgnoreRemainingEvents()
        }
        repository.observeAll().test {
            assertThat(awaitItem()).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
