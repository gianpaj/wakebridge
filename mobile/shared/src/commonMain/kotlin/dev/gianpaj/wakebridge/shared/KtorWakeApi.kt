package dev.gianpaj.wakebridge.shared

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

internal class KtorWakeApi(
    private val configuration: WakeConfiguration,
    private val client: HttpClient = defaultHttpClient(),
) : WakeApi {
    override suspend fun healthCheck(): ApiResult<HealthResult> {
        val validated = validatedConfiguration()
        if (validated is ApiResult.Failure) return validated
        val config = (validated as ApiResult.Success).value

        return execute {
            val response = client.get(config.endpoint("/health")) {
                accept(ContentType.Application.Json)
                addCloudflareHeaders(config)
            }
            response.toHealthResult()
        }
    }

    override suspend fun wake(): ApiResult<WakeResult> {
        val validated = validatedConfiguration()
        if (validated is ApiResult.Failure) return validated
        val config = (validated as ApiResult.Success).value

        return execute {
            val response = client.post(config.endpoint("/wake")) {
                accept(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${config.wakeApiToken}")
                addCloudflareHeaders(config)
            }
            response.toWakeResult()
        }
    }

    override fun close() {
        client.close()
    }

    private fun validatedConfiguration(): ApiResult<WakeConfiguration> =
        when (val validation = configuration.validate()) {
            is ConfigurationValidation.Valid -> ApiResult.Success(validation.configuration)
            is ConfigurationValidation.Invalid -> ApiResult.Failure(
                ApiError.InvalidConfiguration(validation.reason),
            )
        }

    private suspend fun <T> execute(block: suspend () -> ApiResult<T>): ApiResult<T> =
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ApiResult.Failure(ApiError.CannotReachServer)
        }

    private fun io.ktor.client.request.HttpRequestBuilder.addCloudflareHeaders(
        config: WakeConfiguration,
    ) {
        if (config.cloudflareClientId.isNotBlank()) {
            header("CF-Access-Client-Id", config.cloudflareClientId)
            header("CF-Access-Client-Secret", config.cloudflareClientSecret)
        }
    }

    private suspend fun HttpResponse.toHealthResult(): ApiResult<HealthResult> {
        if (status.isCloudflareRejection()) {
            return ApiResult.Failure(ApiError.CloudflareAuthenticationFailed)
        }
        if (status != HttpStatusCode.OK) {
            return ApiResult.Failure(ApiError.ServerRejected(status.value))
        }

        val payload = decodeOrNull<HealthResponse>()
        return if (payload?.ok == true) {
            ApiResult.Success(HealthResult(ok = true))
        } else {
            ApiResult.Failure(ApiError.UnexpectedResponse)
        }
    }

    private suspend fun HttpResponse.toWakeResult(): ApiResult<WakeResult> {
        if (status.isRedirect() || status == HttpStatusCode.Forbidden) {
            return ApiResult.Failure(ApiError.CloudflareAuthenticationFailed)
        }
        if (status == HttpStatusCode.Unauthorized) {
            val error = decodeOrNull<ErrorResponse>()
            return if (error?.error == "unauthorized") {
                ApiResult.Failure(ApiError.WakeTokenRejected)
            } else {
                ApiResult.Failure(ApiError.CloudflareAuthenticationFailed)
            }
        }
        if (status == HttpStatusCode.InternalServerError) {
            return ApiResult.Failure(ApiError.WakePacketFailed)
        }
        if (status != HttpStatusCode.OK) {
            return ApiResult.Failure(ApiError.ServerRejected(status.value))
        }

        val payload = decodeOrNull<WakeResponse>()
        return if (payload?.ok == true && payload.target == TARGET_NAME) {
            ApiResult.Success(WakeResult(ok = true, target = payload.target))
        } else {
            ApiResult.Failure(ApiError.UnexpectedResponse)
        }
    }

    private suspend inline fun <reified T> HttpResponse.decodeOrNull(): T? =
        try {
            json.decodeFromString<T>(body<String>())
        } catch (_: SerializationException) {
            null
        }

    private fun HttpStatusCode.isCloudflareRejection(): Boolean =
        isRedirect() || this == HttpStatusCode.Unauthorized || this == HttpStatusCode.Forbidden

    private fun HttpStatusCode.isRedirect(): Boolean = value in 300..399

    private fun WakeConfiguration.endpoint(path: String): String = serverUrl + path

    @Serializable
    private data class HealthResponse(val ok: Boolean)

    @Serializable
    private data class WakeResponse(
        val ok: Boolean,
        val target: String,
    )

    @Serializable
    private data class ErrorResponse(
        @SerialName("error") val error: String,
    )

    private companion object {
        const val TARGET_NAME = "gianRTX"
        val json = Json { ignoreUnknownKeys = true }

        fun defaultHttpClient(): HttpClient = HttpClient {
            followRedirects = false
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 15_000
                socketTimeoutMillis = 10_000
            }
        }
    }
}
