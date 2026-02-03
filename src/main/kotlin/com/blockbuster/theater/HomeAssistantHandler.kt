package com.blockbuster.theater

import com.blockbuster.media.MediaJson
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpRequest

/**
 * Handler for Home Assistant automation trigger.
 *
 * Sends a POST to the Home Assistant REST API to trigger the specified automation.
 */
class HomeAssistantHandler(
    private val device: TheaterDevice.HomeAssistant,
    private val http: TheaterHttpClient,
) : TheaterDeviceHandler {
    private val logger = LoggerFactory.getLogger(HomeAssistantHandler::class.java)

    override fun setup() {
        logger.info("Triggering Home Assistant automation at {}: {}", device.ip, device.automationId)

        val body = MediaJson.mapper.writeValueAsString(mapOf("entity_id" to device.automationId))
        val request =
            HttpRequest.newBuilder()
                .uri(URI.create("http://${device.ip}:8123/api/services/automation/trigger"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()

        http.sendAndCheck(request, "Home Assistant")
    }
}
