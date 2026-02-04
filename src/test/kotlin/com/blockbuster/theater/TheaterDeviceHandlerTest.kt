package com.blockbuster.theater

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*

class TheaterDeviceHandlerTest {
    // --- HarmonyHubHandler tests ---

    @Test
    fun `HarmonyHubHandler sends correct HTTP request`() {
        val http = mock<TheaterHttpClient>()
        val handler =
            HarmonyHubHandler(
                TheaterDevice.HarmonyHub(ip = "192.168.1.50", activityId = "12345678", delayMs = 0),
                http,
            )

        handler.setup()

        val urlCaptor = argumentCaptor<String>()
        val bodyCaptor = argumentCaptor<String>()
        verify(http).sendAndCheck(urlCaptor.capture(), bodyCaptor.capture(), eq("Harmony Hub"))
        assertEquals("http://192.168.1.50:8088/start", urlCaptor.firstValue)
        assertTrue(bodyCaptor.firstValue?.contains("12345678") == true)
    }

    @Test
    fun `HarmonyHubHandler throws TheaterSetupException on HTTP error`() {
        val http =
            mock<TheaterHttpClient> {
                on { sendAndCheck(any(), anyOrNull(), eq("Harmony Hub")) } doThrow
                    TheaterSetupException("Harmony Hub", "HTTP 500: Internal Server Error")
            }
        val handler =
            HarmonyHubHandler(
                TheaterDevice.HarmonyHub(ip = "192.168.1.50", activityId = "12345678", delayMs = 0),
                http,
            )

        val exception = assertThrows<TheaterSetupException> { handler.setup() }
        assertEquals("Harmony Hub", exception.deviceType)
        assertTrue(exception.message?.contains("HTTP 500") == true)
    }

    // --- HomeAssistantHandler tests ---

    @Test
    fun `HomeAssistantHandler sends correct HTTP request`() {
        val http = mock<TheaterHttpClient>()
        val handler =
            HomeAssistantHandler(
                TheaterDevice.HomeAssistant(ip = "192.168.1.10", automationId = "automation.movie_mode"),
                http,
            )

        handler.setup()

        val urlCaptor = argumentCaptor<String>()
        val bodyCaptor = argumentCaptor<String>()
        verify(http).sendAndCheck(urlCaptor.capture(), bodyCaptor.capture(), eq("Home Assistant"))
        assertEquals("http://192.168.1.10:8123/api/services/automation/trigger", urlCaptor.firstValue)
        assertTrue(bodyCaptor.firstValue?.contains("automation.movie_mode") == true)
    }

    // --- RokuTheaterHandler tests ---

    @Test
    fun `RokuTheaterHandler sends Home keypress`() {
        val http = mock<TheaterHttpClient>()
        val handler =
            RokuTheaterHandler(
                TheaterDevice.Roku(ip = "192.168.1.100"),
                http,
            )

        handler.setup()

        val urlCaptor = argumentCaptor<String>()
        verify(http).sendAndCheck(urlCaptor.capture(), anyOrNull(), eq("Roku"))
        assertEquals("http://192.168.1.100:8060/keypress/Home", urlCaptor.firstValue)
    }

    // --- Factory function tests ---

    @Test
    fun `createTheaterHandler returns null for None`() {
        val http = mock<TheaterHttpClient>()
        assertNull(createTheaterHandler(TheaterDevice.None, http))
    }

    @Test
    fun `createTheaterHandler returns HarmonyHubHandler for HarmonyHub`() {
        val http = mock<TheaterHttpClient>()
        val handler =
            createTheaterHandler(
                TheaterDevice.HarmonyHub(ip = "1.2.3.4", activityId = "abc"),
                http,
            )
        assertTrue(handler is HarmonyHubHandler)
    }

    @Test
    fun `createTheaterHandler returns HomeAssistantHandler for HomeAssistant`() {
        val http = mock<TheaterHttpClient>()
        val handler =
            createTheaterHandler(
                TheaterDevice.HomeAssistant(ip = "1.2.3.4", automationId = "auto.test"),
                http,
            )
        assertTrue(handler is HomeAssistantHandler)
    }

    @Test
    fun `createTheaterHandler returns RokuTheaterHandler for Roku`() {
        val http = mock<TheaterHttpClient>()
        val handler =
            createTheaterHandler(
                TheaterDevice.Roku(ip = "1.2.3.4"),
                http,
            )
        assertTrue(handler is RokuTheaterHandler)
    }
}
