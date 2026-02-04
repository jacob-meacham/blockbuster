package com.blockbuster.theater

import com.blockbuster.media.MediaJson
import org.slf4j.LoggerFactory

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

        val url = "http://${device.ip}:8123/api/services/automation/trigger"
        val body = MediaJson.mapper.writeValueAsString(mapOf("entity_id" to device.automationId))

        http.sendAndCheck(url, body, "Home Assistant")
    }
}
