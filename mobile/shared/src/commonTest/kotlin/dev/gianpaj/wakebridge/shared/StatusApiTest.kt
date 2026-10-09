package dev.gianpaj.wakebridge.shared

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class StatusApiTest {
    @Test
    fun checksTargetWithBothAuthenticationLayers() = runTest {
        for (online in listOf(true, false)) {
            val api = KtorWakeApi(configuration(), HttpClient(MockEngine { request ->
                assertEquals(HttpMethod.Get, request.method)
                assertEquals("/status", request.url.encodedPath)
                assertEquals("Bearer wake-token", request.headers[HttpHeaders.Authorization])
                assertEquals("client-id", request.headers["CF-Access-Client-Id"])
                assertEquals("client-secret", request.headers["CF-Access-Client-Secret"])
                assertEquals("no-cache", request.headers[HttpHeaders.CacheControl])
                respond("""{"ok":true,"target":"gianRTX","online":$online}""",
                    HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }))
            try {
                assertEquals(ApiResult.Success(StatusResult(online)), api.status())
            } finally { api.close() }
        }
    }

    @Test
    fun distinguishesSetupAuthenticationAndBadResponses() = runTest {
        val cases = listOf(
            Triple(503, """{"ok":false,"error":"status not configured"}""", ApiError.StatusNotConfigured),
            Triple(503, """{"ok":false,"error":"status check failed"}""", ApiError.ServerRejected(503)),
            Triple(401, """{"ok":false,"error":"unauthorized"}""", ApiError.WakeTokenRejected),
            Triple(401, "Access denied", ApiError.CloudflareAuthenticationFailed),
            Triple(403, "Access denied", ApiError.CloudflareAuthenticationFailed),
            Triple(302, "Redirect", ApiError.CloudflareAuthenticationFailed),
            Triple(404, "Not found", ApiError.ServerRejected(404)),
            Triple(500, "Error", ApiError.ServerRejected(500)),
            Triple(200, """{"ok":true,"target":"other","online":true}""", ApiError.UnexpectedResponse),
            Triple(200, """{"ok":true,"target":"gianRTX"}""", ApiError.UnexpectedResponse),
            Triple(200, """{"ok":false,"target":"gianRTX","online":true}""", ApiError.UnexpectedResponse),
            Triple(200, "not json", ApiError.UnexpectedResponse),
        )
        for ((code, body, error) in cases) {
            val api = KtorWakeApi(configuration(), HttpClient(MockEngine {
                respond(body, HttpStatusCode.fromValue(code), headersOf(HttpHeaders.ContentType, "application/json"))
            }))
            try { assertEquals(ApiResult.Failure(error), api.status(), "$code $body") }
            finally { api.close() }
        }
    }

    @Test
    fun doesNotTurnCancellationIntoAnApiFailure() = runTest {
        val api = KtorWakeApi(configuration(), HttpClient(MockEngine {
            throw CancellationException("cancelled")
        }))
        try { assertFailsWith<CancellationException> { api.status() } }
        finally { api.close() }
    }
}
