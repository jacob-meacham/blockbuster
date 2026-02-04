package com.blockbuster.theater

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*

class TheaterDeviceTest {
    private val http = mock<TheaterHttpClient>()

    // --- HarmonyHub tests ---

    @Test
    fun `HarmonyHub setup sends correct HTTP request`() {
        val device = HarmonyHubDevice(ip = "192.168.1.50", activityId = "12345678", delayMs = 0)

        device.setup(http)

        val urlCaptor = argumentCaptor<String>()
        val bodyCaptor = argumentCaptor<String>()
        verify(http).sendAndCheck(urlCaptor.capture(), bodyCaptor.capture(), eq("Harmony Hub"))
        assertEquals("http://192.168.1.50:8088/start", urlCaptor.firstValue)
        assertTrue(bodyCaptor.firstValue.contains("12345678"))
    }

    @Test
    fun `HarmonyHub setup throws TheaterSetupException on HTTP error`() {
        whenever(http.sendAndCheck(any(), anyOrNull(), eq("Harmony Hub")))
            .thenThrow(TheaterSetupException("Harmony Hub", "HTTP 500: Internal Server Error"))
        val device = HarmonyHubDevice(ip = "192.168.1.50", activityId = "12345678", delayMs = 0)

        val exception = assertThrows<TheaterSetupException> { device.setup(http) }
        assertEquals("Harmony Hub", exception.deviceType)
        assertTrue(exception.message?.contains("HTTP 500") == true)
    }

    @Test
    fun `HarmonyHub rejects blank ip`() {
        assertThrows<IllegalArgumentException> {
            HarmonyHubDevice(ip = "", activityId = "12345678")
        }
    }

    @Test
    fun `HarmonyHub rejects blank activityId`() {
        assertThrows<IllegalArgumentException> {
            HarmonyHubDevice(ip = "192.168.1.50", activityId = "")
        }
    }

    @Test
    fun `HarmonyHub rejects out-of-range delayMs`() {
        assertThrows<IllegalArgumentException> {
            HarmonyHubDevice(ip = "192.168.1.50", activityId = "123", delayMs = 60000)
        }
    }

    // --- HomeAssistant tests ---

    @Test
    fun `HomeAssistant setup sends correct HTTP request`() {
        val device = HomeAssistantDevice(ip = "192.168.1.10", automationId = "automation.movie_mode")

        device.setup(http)

        val urlCaptor = argumentCaptor<String>()
        val bodyCaptor = argumentCaptor<String>()
        verify(http).sendAndCheck(urlCaptor.capture(), bodyCaptor.capture(), eq("Home Assistant"))
        assertEquals("http://192.168.1.10:8123/api/services/automation/trigger", urlCaptor.firstValue)
        assertTrue(bodyCaptor.firstValue.contains("automation.movie_mode"))
    }

    // --- Roku tests ---

    @Test
    fun `Roku setup sends Home keypress`() {
        val device = RokuDevice(ip = "192.168.1.100")

        device.setup(http)

        val urlCaptor = argumentCaptor<String>()
        verify(http).sendAndCheck(urlCaptor.capture(), anyOrNull(), eq("Roku"))
        assertEquals("http://192.168.1.100:8060/keypress/Home", urlCaptor.firstValue)
    }

    // --- None tests ---

    @Test
    fun `None setup is no-op`() {
        TheaterDevice.None.setup(http)

        verifyNoInteractions(http)
    }
}
