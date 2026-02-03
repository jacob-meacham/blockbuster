package com.blockbuster.theater

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.*

class TheaterDeviceManagerTest {

    @Test
    fun `setupTheater with unknown deviceId throws IllegalArgumentException`() {
        val manager = TheaterDeviceManager(emptyMap())

        val exception = assertThrows<IllegalArgumentException> {
            manager.setupTheater("unknown-device")
        }
        assertTrue(exception.message?.contains("Unknown device") == true)
    }

    @Test
    fun `setupTheater with null handler does nothing`() {
        val manager = TheaterDeviceManager(
            mapOf("test-device" to null)
        )

        manager.setupTheater("test-device")
    }

    @Test
    fun `setupTheater delegates to handler`() {
        val mockHandler = mock<TheaterDeviceHandler>()
        val manager = TheaterDeviceManager(
            mapOf("living-room" to mockHandler)
        )

        manager.setupTheater("living-room")

        verify(mockHandler).setup()
    }

    @Test
    fun `setupTheater propagates handler exceptions`() {
        val mockHandler = mock<TheaterDeviceHandler> {
            on { setup() } doThrow TheaterSetupException("TestDevice", "HTTP 500: error")
        }
        val manager = TheaterDeviceManager(
            mapOf("living-room" to mockHandler)
        )

        assertThrows<TheaterSetupException> {
            manager.setupTheater("living-room")
        }
    }

    @Test
    fun `multiple appliances route independently`() {
        val handlerA = mock<TheaterDeviceHandler>()
        val manager = TheaterDeviceManager(
            mapOf(
                "living-room" to handlerA,
                "kitchen" to null
            )
        )

        // Null handler: no interaction
        manager.setupTheater("kitchen")
        verifyNoInteractions(handlerA)

        // Real handler: setup called
        manager.setupTheater("living-room")
        verify(handlerA).setup()

        // Unknown device: throws
        assertThrows<IllegalArgumentException> {
            manager.setupTheater("garage")
        }
    }
}
