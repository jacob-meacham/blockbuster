package com.blockbuster.theater

import com.blockbuster.media.MediaJson
import org.slf4j.LoggerFactory

/**
 * Handler for Logitech Harmony Hub theater setup.
 *
 * Sends a POST to the Harmony Hub API to start the specified activity,
 * then waits for the configured delay to allow equipment to power on.
 */
class HarmonyHubHandler(
    private val device: TheaterDevice.HarmonyHub,
    private val http: TheaterHttpClient,
) : TheaterDeviceHandler {
    private val logger = LoggerFactory.getLogger(HarmonyHubHandler::class.java)

    override fun setup() {
        logger.info("Setting up Harmony Hub at {}, activity {}", device.ip, device.activityId)

        val url = "http://${device.ip}:8088/start"
        val body = MediaJson.mapper.writeValueAsString(mapOf("activityId" to device.activityId))

        http.sendAndCheck(url, body, "Harmony Hub")

        if (device.delayMs > 0) {
            try {
                Thread.sleep(device.delayMs)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                logger.warn("Harmony Hub delay interrupted")
            }
        }
    }
}
