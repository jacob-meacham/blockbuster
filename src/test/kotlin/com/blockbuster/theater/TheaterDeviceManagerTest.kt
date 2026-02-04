package com.blockbuster.theater

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*

class TheaterDeviceManagerTest {
    private val mockHttp = mock<TheaterHttpClient>()

    @Test
    fun `setupTheater with unknown deviceId throws IllegalArgumentException`() {
        val manager = TheaterDeviceManager(emptyMap(), mockHttp)

        val exception =
            assertThrows<IllegalArgumentException> {
                manager.setupTheater("unknown-device")
            }
        assertTrue(exception.message?.contains("Unknown device") == true)
    }

    @Test
    fun `setupTheater with None device does nothing`() {
        val manager =
            TheaterDeviceManager(
                mapOf("test-device" to TheaterDevice.None),
                mockHttp,
            )

        manager.setupTheater("test-device")

        verifyNoInteractions(mockHttp)
    }

    @Test
    fun `setupTheater delegates to device setup`() {
        val device = RokuDevice(ip = "192.168.1.100")
        val manager =
            TheaterDeviceManager(
                mapOf("living-room" to device),
                mockHttp,
            )

        manager.setupTheater("living-room")

        verify(mockHttp).sendAndCheck(any(), anyOrNull(), any())
    }

    @Test
    fun `setupTheater propagates device exceptions`() {
        whenever(mockHttp.sendAndCheck(any(), anyOrNull(), any()))
            .thenThrow(TheaterSetupException("Roku", "HTTP 500: error"))
        val device = RokuDevice(ip = "192.168.1.100")
        val manager =
            TheaterDeviceManager(
                mapOf("living-room" to device),
                mockHttp,
            )

        assertThrows<TheaterSetupException> {
            manager.setupTheater("living-room")
        }
    }

    @Test
    fun `multiple appliances route independently`() {
        val device = RokuDevice(ip = "192.168.1.100")
        val manager =
            TheaterDeviceManager(
                mapOf(
                    "living-room" to device,
                    "kitchen" to TheaterDevice.None,
                ),
                mockHttp,
            )

        // None device: no HTTP interaction
        manager.setupTheater("kitchen")
        verifyNoInteractions(mockHttp)

        // Real device: setup called
        manager.setupTheater("living-room")
        verify(mockHttp).sendAndCheck(any(), anyOrNull(), any())

        // Unknown device: throws
        assertThrows<IllegalArgumentException> {
            manager.setupTheater("garage")
        }
    }
}
