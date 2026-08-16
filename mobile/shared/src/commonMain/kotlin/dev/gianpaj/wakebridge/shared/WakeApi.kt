package dev.gianpaj.wakebridge.shared

data class HealthResult(val ok: Boolean)

data class WakeResult(
    val ok: Boolean,
    val target: String,
)

sealed interface ApiResult<out T> {
    data class Success<T>(val value: T) : ApiResult<T>
    data class Failure(val error: ApiError) : ApiResult<Nothing>
}

sealed interface ApiError {
    data class InvalidConfiguration(val reason: ConfigurationError) : ApiError
    data object CannotReachServer : ApiError
    data object CloudflareAuthenticationFailed : ApiError
    data object WakeTokenRejected : ApiError
    data class ServerRejected(val status: Int) : ApiError
    data object WakePacketFailed : ApiError
    data object UnexpectedResponse : ApiError
}

interface WakeApi {
    suspend fun healthCheck(): ApiResult<HealthResult>
    suspend fun wake(): ApiResult<WakeResult>
    fun close()
}

object WakeApiFactory {
    fun create(configuration: WakeConfiguration): WakeApi = KtorWakeApi(configuration)
}
