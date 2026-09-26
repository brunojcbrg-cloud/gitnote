package io.github.wiiznokes.gitnote.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupSyncGateTest {
    @Test
    fun `pull suspenso mantem editor bloqueado`() = runBlocking {
        var clock = 1_000L
        val gate = StartupSyncGate { clock }
        val pull = CompletableDeferred<Result<Unit>>()

        val running = async { gate.run(force = true) { pull.await() } }
        yield()

        assertTrue(gate.state.value is StartupSyncState.Syncing)
        assertFalse(gate.editingAllowed())

        clock += 5_000
        pull.complete(Result.success(Unit))
        running.await()
        assertTrue(gate.state.value is StartupSyncState.Synced)
        assertTrue(gate.editingAllowed())
    }

    @Test
    fun `falha mantem bloqueio ate escolha explicita`() = runBlocking {
        val gate = StartupSyncGate { 1_000L }
        gate.run(force = true) { Result.failure(IllegalStateException("sem rede")) }

        assertTrue(gate.state.value is StartupSyncState.Failed)
        assertFalse(gate.editingAllowed())

        gate.editAnyway()
        assertTrue(gate.state.value is StartupSyncState.Override)
        assertTrue(gate.editingAllowed())
    }

    @Test
    fun `retorno ao app respeita intervalo minimo`() = runBlocking {
        var clock = 10_000L
        var calls = 0
        val gate = StartupSyncGate { clock }
        gate.run(force = true) { calls += 1; Result.success(Unit) }

        clock += STARTUP_SYNC_MIN_INTERVAL_MS - 1
        val skipped = gate.run(force = false) { calls += 1; Result.success(Unit) }
        assertTrue(skipped == null)

        clock += 1
        gate.run(force = false) { calls += 1; Result.success(Unit) }
        assertTrue(calls == 2)
    }
}
