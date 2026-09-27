package io.github.wiiznokes.gitnote.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class BackgroundStartupTest {
    @Test
    fun `pedido de sincronizacao volta antes do corpo e usa outra thread`() = runBlocking {
        val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "git-io-test") }
        val dispatcher = executor.asCoroutineDispatcher()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val dispatcherBlocked = CountDownLatch(1)
        val releaseDispatcher = CountDownLatch(1)
        executor.execute {
            dispatcherBlocked.countDown()
            releaseDispatcher.await()
        }
        assertTrue(dispatcherBlocked.await(2, TimeUnit.SECONDS))

        val callerThread = Thread.currentThread().name
        val bodyThread = CompletableDeferred<String>()
        val runner = AsyncSyncRunner(scope, dispatcher, { true }) {
            bodyThread.complete(Thread.currentThread().name)
        }

        runner.request(force = true)
        assertFalse(bodyThread.isCompleted)
        releaseDispatcher.countDown()
        assertNotEquals(callerThread, bodyThread.await())

        scope.cancel()
        dispatcher.close()
        executor.shutdownNow()
    }

    @Test
    fun `inicializacao roda uma vez mesmo com tres voltas ao app`() = runBlocking {
        var calls = 0
        val completed = CompletableDeferred<Unit>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val initialization = ProcessInitialization(scope, Dispatchers.Default) {
            calls += 1
            completed.complete(Unit)
            true
        }

        repeat(3) { initialization.start() }
        completed.await()
        val ready = initialization.state.first { it is InitializationState.Ready }

        assertEquals(1, calls)
        assertEquals(InitializationState.Ready(configured = true), ready)
        scope.cancel()
    }
}
