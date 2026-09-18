package dev.gianpaj.wakebridge.shared

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AwaitOnlineTest {
    @Test
    fun checksImmediatelyAndStopsWhenOnline() = runTest {
        val api = StatusApi { ApiResult.Success(StatusResult(online = true)) }
        assertEquals(OnlineResult.Online, api.awaitOnline())
        assertEquals(1, api.checks)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun waitsTwoSecondsBetweenChecks() = runTest {
        var checks = 0
        val api = StatusApi { ApiResult.Success(StatusResult(online = ++checks == 3)) }
        assertEquals(OnlineResult.Online, api.awaitOnline())
        assertEquals(3, api.checks)
        assertEquals(4_000L, testScheduler.currentTime)
    }

    @Test
    fun stopsAfterNinetySeconds() = runTest {
        val api = StatusApi { ApiResult.Success(StatusResult(online = false)) }
        assertEquals(OnlineResult.TimedOut, api.awaitOnline())
        assertEquals(90_000L, testScheduler.currentTime)
        assertEquals(45, api.checks)
    }

    @Test
    fun deadlineCancelsAnInFlightRequest() = runTest {
        var cancelled = false
        val api = StatusApi {
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }
        assertEquals(OnlineResult.TimedOut, api.awaitOnline())
        assertTrue(cancelled)
        assertEquals(90_000L, testScheduler.currentTime)
    }

    @Test
    fun reportsApiFailureWithoutWaitingOrRetrying() = runTest {
        val api = StatusApi { ApiResult.Failure(ApiError.CannotReachServer) }
        assertEquals(OnlineResult.Failed(ApiError.CannotReachServer), api.awaitOnline())
        assertEquals(1, api.checks)
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun callerCancellationPropagates() = runTest {
        val started = CompletableDeferred<Unit>()
        var returned = false
        var cancelled = false
        val api = StatusApi {
            started.complete(Unit)
            try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
        }
        val job = launch {
            api.awaitOnline()
            returned = true
        }
        started.await()
        job.cancel()
        job.join()
        assertTrue(cancelled)
        assertTrue(!returned)
        assertEquals(1, api.checks)
    }
}

private class StatusApi(
    private val check: suspend () -> ApiResult<StatusResult>,
) : WakeApi {
    var checks = 0
    override suspend fun status(): ApiResult<StatusResult> {
        checks++
        return check()
    }
    override suspend fun wake(): ApiResult<WakeResult> = error("Polling must not wake the target")
    override suspend fun healthCheck(): ApiResult<HealthResult> = error("Polling must check the target")
    override fun close() = Unit
}
