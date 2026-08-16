package dev.gianpaj.wakebridge.shared

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.io.IOException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KtorWakeApiTest {
    @Test
    fun healthCheckUsesGetAndCloudflareHeaders() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/health", request.url.encodedPath)
            assertEquals("client-id", request.headers["CF-Access-Client-Id"])
            assertEquals("client-secret", request.headers["CF-Access-Client-Secret"])
            respond(
                content = """{"ok":true}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = KtorWakeApi(configuration(), HttpClient(engine))

        val result = api.healthCheck()

        assertIs<ApiResult.Success<HealthResult>>(result)
        api.close()
    }

    @Test
    fun wakeUsesPostAndBothAuthenticationLayers() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/wake", request.url.encodedPath)
            assertEquals("Bearer wake-token", request.headers[HttpHeaders.Authorization])
            assertEquals("client-id", request.headers["CF-Access-Client-Id"])
            respond(
                content = """{"ok":true,"target":"gianRTX"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = KtorWakeApi(configuration(), HttpClient(engine))

        val result = api.wake()

        val success = assertIs<ApiResult.Success<WakeResult>>(result)
        assertEquals("gianRTX", success.value.target)
        api.close()
    }

    @Test
    fun invalidWakeTokenIsReported() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"ok":false,"error":"unauthorized"}""",
                status = HttpStatusCode.Unauthorized,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        val api = KtorWakeApi(configuration(), HttpClient(engine))

        val result = api.wake()

        val failure = assertIs<ApiResult.Failure>(result)
        assertIs<ApiError.WakeTokenRejected>(failure.error)
        api.close()
    }

    @Test
    fun serverFailureReportsStatus() = runTest {
        val engine = MockEngine { respondError(HttpStatusCode.ServiceUnavailable) }
        val api = KtorWakeApi(configuration(), HttpClient(engine))

        val result = api.healthCheck()

        val failure = assertIs<ApiResult.Failure>(result)
        assertEquals(ApiError.ServerRejected(503), failure.error)
        api.close()
    }

    @Test
    fun transportFailureIsReportedWithoutDetails() = runTest {
        val engine = MockEngine { throw IOException("offline") }
        val api = KtorWakeApi(configuration(), HttpClient(engine))

        val result = api.healthCheck()

        val failure = assertIs<ApiResult.Failure>(result)
        assertEquals(ApiError.CannotReachServer, failure.error)
        api.close()
    }
}
