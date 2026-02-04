package com.blockbuster.theater

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DefaultTheaterHttpClientTest {
    private lateinit var server: MockWebServer

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `succeeds on 2xx response`() {
        server.enqueue(MockResponse().setResponseCode(200))
        val http = DefaultTheaterHttpClient(okhttp3.OkHttpClient())

        val url = server.url("/test").toString()
        http.sendAndCheck(url, null, "TestDevice")
    }

    @Test
    fun `throws TheaterSetupException on non-2xx response`() {
        server.enqueue(MockResponse().setResponseCode(503).setBody("Service Unavailable"))
        val http = DefaultTheaterHttpClient(okhttp3.OkHttpClient())

        val url = server.url("/test").toString()
        val exception =
            assertThrows<TheaterSetupException> {
                http.sendAndCheck(url, null, "TestDevice")
            }
        assertEquals("TestDevice", exception.deviceType)
        assertTrue(exception.message?.contains("HTTP 503") == true)
    }
}
