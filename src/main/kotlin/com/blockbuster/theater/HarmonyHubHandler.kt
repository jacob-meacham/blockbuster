package com.blockbuster.theater

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpRequest

/**
 * Handler for Logitech Harmony Hub theater setup.
 *
 * Sends a POST to the Harmony Hub API to start the specified activity,
 * then waits for the configured delay to allow equipment to power on.
 */
class HarmonyHubHandler(
    private val device: TheaterDevice.HarmonyHub,
    private val http: TheaterHttpClient
) : TheaterDeviceHandler {

    private val logger = LoggerFactory.getLogger(HarmonyHubHandler::class.java)

    private companion object {
        val objectMapper = ObjectMapper()
    }

    override fun setup() {
        logger.info("Setting up Harmony Hub at {}, activity {}", device.ip, device.activityId)

        val body = objectMapper.writeValueAsString(mapOf("activityId" to device.activityId))
        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://${device.ip}:8088/start"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()

        http.sendAndCheck(request, "Harmony Hub")

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
