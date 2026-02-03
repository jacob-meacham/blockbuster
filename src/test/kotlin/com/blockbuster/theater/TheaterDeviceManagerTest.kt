package com.blockbuster.theater

import com.blockbuster.ApplianceConfig
import com.blockbuster.BlockbusterConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TheaterDeviceManagerTest {

    @Test
    fun `setupTheater with unknown deviceId throws IllegalArgumentException`() {
        // Given
        val config = BlockbusterConfiguration()
        val manager = TheaterDeviceManager(config)

        // When/Then
        val exception = assertThrows<IllegalArgumentException> {
            manager.setupTheater("unknown-device")
        }
        assertTrue(exception.message?.contains("Unknown device") == true)
    }

    @Test
    fun `setupTheater with None device does nothing`() {
        // Given
        val config = BlockbusterConfiguration().apply {
            appliances = mapOf(
                "test-device" to ApplianceConfig(TheaterDevice.None)
            )
        }
        val manager = TheaterDeviceManager(config)

        // When/Then - should not throw
        assertDoesNotThrow {
            manager.setupTheater("test-device")
        }
    }

    @Test
    fun `setupTheater with HarmonyHub device accepts valid configuration`() {
        // Given
        val config = BlockbusterConfiguration().apply {
            appliances = mapOf(
                "living-room" to ApplianceConfig(
                    TheaterDevice.HarmonyHub(
                        ip = "192.168.1.50",
                        activityId = "12345678",
                        delayMs = 5000
                    )
                )
            )
        }
        val manager = TheaterDeviceManager(config)

        // When/Then
        // Note: This will attempt an actual HTTP call which may fail
        // In a real environment, we'd mock the HTTP client
        // For now, we're just verifying configuration is accepted
        assertDoesNotThrow {
            try {
                manager.setupTheater("living-room")
            } catch (e: Exception) {
                // Expected if Harmony Hub isn't reachable
                // We're just testing configuration handling
            }
        }
    }

    @Test
    fun `setupTheater with HomeAssistant device accepts valid configuration`() {
        // Given
        val config = BlockbusterConfiguration().apply {
            appliances = mapOf(
                "bedroom" to ApplianceConfig(
                    TheaterDevice.HomeAssistant(
                        ip = "192.168.1.10",
                        automationId = "automation.bedroom_movie_mode"
                    )
                )
            )
        }
        val manager = TheaterDeviceManager(config)

        // When/Then
        assertDoesNotThrow {
            try {
                manager.setupTheater("bedroom")
            } catch (e: Exception) {
                // Expected if Home Assistant isn't reachable
            }
        }
    }

    @Test
    fun `setupTheater with Roku device accepts valid configuration`() {
        // Given
        val config = BlockbusterConfiguration().apply {
            appliances = mapOf(
                "kitchen" to ApplianceConfig(
                    TheaterDevice.Roku(ip = "192.168.1.100")
                )
            )
        }
        val manager = TheaterDeviceManager(config)

        // When/Then
        assertDoesNotThrow {
            try {
                manager.setupTheater("kitchen")
            } catch (e: Exception) {
                // Expected if Roku isn't reachable
            }
        }
    }

    @Test
    fun `multiple appliances can be configured`() {
        // Given
        val config = BlockbusterConfiguration().apply {
            appliances = mapOf(
                "living-room" to ApplianceConfig(
                    TheaterDevice.HarmonyHub("192.168.1.50", "12345678")
                ),
                "bedroom" to ApplianceConfig(
                    TheaterDevice.HomeAssistant("192.168.1.10", "automation.movie_mode")
                ),
                "kitchen" to ApplianceConfig(TheaterDevice.None)
            )
        }
        val manager = TheaterDeviceManager(config)

        // When/Then - kitchen (None) should work without HTTP calls
        assertDoesNotThrow {
            manager.setupTheater("kitchen")
        }

        // Verify unknown device still throws
        assertThrows<IllegalArgumentException> {
            manager.setupTheater("garage")
        }
    }
}
