package io.github.wiiznokes.gitnote.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupSyncGateTest {
    @Test
    fun `tela cheia so no arranque e faixa no retorno`() {
        val states = listOf<StartupSyncState>(
            StartupSyncState.Idle,
            StartupSyncState.Syncing(1),
            StartupSyncState.Synced(1, 1_000),
            StartupSyncState.Failed(1, "sem rede"),
            StartupSyncState.Override(1),
        )

        assertEquals(
            listOf(
                SyncPresentationMode.FullScreen,
                SyncPresentationMode.FullScreen,
                SyncPresentationMode.Free,
                SyncPresentationMode.FullScreen,
                SyncPresentationMode.Free,
            ),
            states.map { syncPresentationMode(it, firstAttemptFinished = false) },
        )
        assertEquals(
            listOf(
                SyncPresentationMode.FullScreen,
                SyncPresentationMode.Banner,
                SyncPresentationMode.Free,
                SyncPresentationMode.Banner,
                SyncPresentationMode.Free,
            ),
            states.map { syncPresentationMode(it, firstAttemptFinished = true) },
        )
    }

    @Test
    fun `progresso por etapa e monotono de zero a cem`() {
        val reducer = SyncProgressReducer()
        val values = listOf(
            reducer.reset().percent,
            reducer.apply(SyncProgressEvent.CheckingLocalChanges).percent,
            reducer.apply(SyncProgressEvent.Downloading(1, 4)).percent,
            reducer.apply(SyncProgressEvent.Downloading(4, 4)).percent,
            reducer.apply(SyncProgressEvent.Merging).percent,
            reducer.apply(SyncProgressEvent.Uploading(1, 2)).percent,
            reducer.apply(SyncProgressEvent.Indexing(5, 10, "nota.md")).percent,
            reducer.apply(SyncProgressEvent.Finished).percent,
        )

        assertEquals(0, values.first())
        assertEquals(100, values.last())
        assertEquals(values.sorted(), values)
    }

    @Test
    fun `etapa sem total fica indeterminada dentro da faixa`() {
        val reducer = SyncProgressReducer()
        reducer.reset()
        val download = reducer.apply(SyncProgressEvent.Downloading(0, 0))
        val upload = reducer.apply(SyncProgressEvent.Uploading(0, 0))

        assertEquals(10, download.percent)
        assertTrue(download.indeterminate)
        assertEquals(80, upload.percent)
        assertTrue(upload.indeterminate)
    }

    @Test
    fun `limite de tempo vira falha legivel`() = runBlocking {
        val gate = StartupSyncGate(now = { 1_000L }, timeoutMs = 1L)
        gate.run(force = true) {
            delay(50)
            Result.success(Unit)
        }

        val failed = gate.state.value as StartupSyncState.Failed
        assertEquals("demorou demais", failed.message)
    }

    @Test
    fun `pull suspenso mantem editor bloqueado`() = runBlocking {
        var clock = 1_000L
        val gate = StartupSyncGate(now = { clock })
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
        val gate = StartupSyncGate(now = { 1_000L })
        gate.run(force = true) { Result.failure(IllegalStateException("sem rede")) }

        assertTrue(gate.state.value is StartupSyncState.Failed)
        assertTrue(readingAllowed(gate.state.value, firstAttemptFinished = true))
        assertFalse(gate.editingAllowed())

        gate.editAnyway()
        assertTrue(gate.state.value is StartupSyncState.Override)
        assertTrue(readingAllowed(gate.state.value, firstAttemptFinished = true))
        assertTrue(gate.editingAllowed())
    }

    @Test
    fun `retorno ao app respeita intervalo minimo`() = runBlocking {
        var clock = 10_000L
        var calls = 0
        val gate = StartupSyncGate(now = { clock })
        gate.run(force = true) { calls += 1; Result.success(Unit) }

        clock += STARTUP_SYNC_MIN_INTERVAL_MS - 1
        val skipped = gate.run(force = false) { calls += 1; Result.success(Unit) }
        assertTrue(skipped == null)

        clock += 1
        gate.run(force = false) { calls += 1; Result.success(Unit) }
        assertTrue(calls == 2)
    }
}
