package dev.gianpaj.wakebridge.shared

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

sealed interface OnlineResult {
    data object Online : OnlineResult
    data object TimedOut : OnlineResult
    data class Failed(val error: ApiError) : OnlineResult
}

// The deadline includes requests and delays. Caller cancellation propagates.
suspend fun WakeApi.awaitOnline(): OnlineResult = withTimeoutOrNull(90_000L) {
    var result = status()
    while (result is ApiResult.Success && !result.value.online) {
        delay(2_000L)
        result = status()
    }
    when (result) {
        is ApiResult.Success -> OnlineResult.Online
        is ApiResult.Failure -> OnlineResult.Failed(result.error)
    }
} ?: OnlineResult.TimedOut
