package com.blockbuster.theater

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class DefaultTheaterHttpClientTest {

    private fun mockHttpClient(statusCode: Int = 200, body: String = ""): HttpClient {
        val response = mock<HttpResponse<String>> {
            on { statusCode() } doReturn statusCode
            on { body() } doReturn body
        }
        return mock {
            on { send(any<HttpRequest>(), any<HttpResponse.BodyHandler<String>>()) } doReturn response
        }
    }

    @Test
    fun `succeeds on 2xx response`() {
        val httpClient = mockHttpClient(statusCode = 200)
        val http = DefaultTheaterHttpClient(httpClient)

        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost/test"))
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .build()

        http.sendAndCheck(request, "TestDevice")
    }

    @Test
    fun `throws TheaterSetupException on non-2xx response`() {
        val httpClient = mockHttpClient(statusCode = 503, body = "Service Unavailable")
        val http = DefaultTheaterHttpClient(httpClient)

        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost/test"))
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .build()

        val exception = assertThrows<TheaterSetupException> {
            http.sendAndCheck(request, "TestDevice")
        }
        assertEquals("TestDevice", exception.deviceType)
        assertTrue(exception.message?.contains("HTTP 503") == true)
    }
}
