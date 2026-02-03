package com.blockbuster.theater

import org.slf4j.LoggerFactory

/**
 * Manages theater device setup for media playback.
 *
 * Coordinates theater setup by delegating to device-specific [TheaterDeviceHandler]
 * instances. Does not know about specific device types.
 *
 * @param handlers Map of appliance device IDs to their handlers.
 *   Null values represent devices with no theater setup (e.g., [TheaterDevice.None]).
 */
class TheaterDeviceManager(
    private val handlers: Map<String, TheaterDeviceHandler?>,
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
        require(handlers.containsKey(deviceId)) { "Unknown device: $deviceId" }

        val handler = handlers[deviceId]
        if (handler == null) {
            logger.debug("No theater setup for device: {}", deviceId)
            return
        }

        handler.setup()
    }
}
