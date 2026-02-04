package com.blockbuster.theater

import org.slf4j.LoggerFactory

/**
 * Manages theater device setup for media playback.
 *
 * Coordinates theater setup by delegating to device-specific [TheaterDevice.setup] methods.
 *
 * @param devices Map of appliance device IDs to their theater devices.
 * @param http The HTTP client used for theater device communication.
 */
class TheaterDeviceManager(
    private val devices: Map<String, TheaterDevice>,
    private val http: TheaterHttpClient,
) {
    private val logger = LoggerFactory.getLogger(TheaterDeviceManager::class.java)

    /**
     * Setup theater for the given appliance device ID.
     *
     * @param deviceId The appliance device ID (e.g., "living-room")
     * @throws IllegalArgumentException if deviceId is unknown
     * @throws TheaterSetupException if the device setup fails
     */
    fun setupTheater(deviceId: String) {
        val device =
            devices[deviceId]
                ?: throw IllegalArgumentException("Unknown device: $deviceId")

        if (device is TheaterDevice.None) {
            logger.debug("No theater setup for device: {}", deviceId)
            return
        }

        device.setup(http)
    }
}
