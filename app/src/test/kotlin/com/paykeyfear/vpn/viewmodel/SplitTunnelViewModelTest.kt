package com.paykeyfear.vpn.viewmodel

import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.paykeyfear.vpn.core.model.SplitTunnelMode
import com.paykeyfear.vpn.service.TunnelController
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SplitTunnelViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val context =
        ApplicationProvider.getApplicationContext<android.content.Context>()
    private val tunnelController = TunnelController(emptyMap())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state reflects preferences mode and selection`() = runTest(dispatcher) {
        val prefs = FakePreferencesRepository(
            splitMode = SplitTunnelMode.Allowlist,
            splitPackages = setOf("com.example.a", "com.example.b"),
        )
        val vm = SplitTunnelViewModel(context, prefs, tunnelController)
        vm.state.test {
            var s = awaitItem()
            while (s.mode == SplitTunnelMode.Off || s.selected.isEmpty()) s = awaitItem()
            assertThat(s.mode).isEqualTo(SplitTunnelMode.Allowlist)
            assertThat(s.selected).containsExactly("com.example.a", "com.example.b")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setMode writes through to preferences`() = runTest(dispatcher) {
        val prefs = FakePreferencesRepository()
        val vm = SplitTunnelViewModel(context, prefs, tunnelController)
        vm.setMode(SplitTunnelMode.Denylist)
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(prefs.currentSplitMode()).isEqualTo(SplitTunnelMode.Denylist)
    }

    @Test
    fun `toggle adds and removes packages`() = runTest(dispatcher) {
        val prefs = FakePreferencesRepository(splitPackages = setOf("com.existing"))
        val vm = SplitTunnelViewModel(context, prefs, tunnelController)
        dispatcher.scheduler.advanceUntilIdle()

        vm.toggle("com.added", checked = true)
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(prefs.currentSplitPackages()).containsExactly("com.existing", "com.added")

        vm.toggle("com.existing", checked = false)
        dispatcher.scheduler.advanceUntilIdle()
        assertThat(prefs.currentSplitPackages()).containsExactly("com.added")
    }

    @Test
    fun `query filters apps by label and package substring`() = runTest(dispatcher) {
        val prefs = FakePreferencesRepository()
        val vm = SplitTunnelViewModel(context, prefs, tunnelController)
        dispatcher.scheduler.advanceUntilIdle()

        val sample = listOf(
            InstalledApp("com.foo.browser", "Foo Browser", isSystem = false),
            InstalledApp("org.bar.mail", "Bar Mail", isSystem = false),
            InstalledApp("com.baz.chat", "Baz Chat", isSystem = true),
        )
        val state = SplitTunnelUiState(apps = sample, query = "mail")
        assertThat(state.filtered.map { it.packageName })
            .containsExactly("org.bar.mail")

        val byPackage = SplitTunnelUiState(apps = sample, query = "baz")
        assertThat(byPackage.filtered.map { it.packageName })
            .containsExactly("com.baz.chat")
    }

    @Test
    fun `setQuery updates state`() = runTest(dispatcher) {
        val prefs = FakePreferencesRepository()
        val vm = SplitTunnelViewModel(context, prefs, tunnelController)
        vm.setQuery("browser")
        vm.state.test {
            var s = awaitItem()
            while (s.query != "browser") s = awaitItem()
            assertThat(s.query).isEqualTo("browser")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
