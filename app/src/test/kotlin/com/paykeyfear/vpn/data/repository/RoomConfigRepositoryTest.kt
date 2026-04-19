package com.paykeyfear.vpn.data.repository

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.paykeyfear.vpn.core.model.ConnectionConfig
import com.paykeyfear.vpn.core.model.Protocol
import com.paykeyfear.vpn.testing.FakeConfigDao
import com.paykeyfear.vpn.testing.testVlessConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoomConfigRepositoryTest {
    private val dao = FakeConfigDao()
    private val json = Json { ignoreUnknownKeys = true }
    private var now: Long = 1_000L
    private val repo = RoomConfigRepository(dao, json, clock = { now })

    @Test
    fun `upsert then findById round-trips the full ConnectionConfig payload`() = runTest {
        val cfg = testVlessConfig(id = "a1", displayName = "Alpha")
        repo.upsert(cfg)
        val loaded = repo.findById("a1")
        assertThat(loaded).isEqualTo(cfg)
    }

    @Test
    fun `upsert persists denormalized columns alongside payload`() = runTest {
        val cfg = testVlessConfig(id = "a1", displayName = "Alpha", host = "h.example", port = 8443)
        repo.upsert(cfg)
        val row = dao.findById("a1")!!
        assertThat(row.protocol).isEqualTo(Protocol.VLESS.name)
        assertThat(row.displayName).isEqualTo("Alpha")
        assertThat(row.endpointHost).isEqualTo("h.example")
        assertThat(row.endpointPort).isEqualTo(8443)
        assertThat(row.createdAtEpochMs).isEqualTo(1_000L)
    }

    @Test
    fun `upsert with same id replaces previous row`() = runTest {
        val first = testVlessConfig(id = "a1", displayName = "Old")
        val second = testVlessConfig(id = "a1", displayName = "New")
        repo.upsert(first)
        repo.upsert(second)
        val loaded = repo.findById("a1") as ConnectionConfig.Vless
        assertThat(loaded.displayName).isEqualTo("New")
    }

    @Test
    fun `delete removes the row`() = runTest {
        val cfg = testVlessConfig(id = "a1")
        repo.upsert(cfg)
        repo.delete("a1")
        assertThat(repo.findById("a1")).isNull()
    }

    @Test
    fun `markUsed updates lastUsed timestamp so row sorts first`() = runTest {
        now = 1_000L
        repo.upsert(testVlessConfig(id = "first"))
        now = 2_000L
        repo.upsert(testVlessConfig(id = "second"))
        repo.markUsed("first", epochMs = 5_000L)

        repo.observeAll().test {
            val sorted = awaitItem()
            assertThat(sorted.map { it.id }).containsExactly("first", "second").inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeAll emits newly upserted configs`() = runTest {
        repo.observeAll().test {
            assertThat(awaitItem()).isEmpty()
            repo.upsert(testVlessConfig(id = "a"))
            assertThat(awaitItem().map { it.id }).containsExactly("a")
            cancelAndIgnoreRemainingEvents()
        }
    }
}
