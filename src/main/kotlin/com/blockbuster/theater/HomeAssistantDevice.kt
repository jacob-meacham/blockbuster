package com.blockbuster.theater

import com.blockbuster.media.MediaJson
import org.slf4j.LoggerFactory

/**
 * Home Assistant for triggering automations.
 *
 * Sends a POST to the Home Assistant REST API to trigger the specified automation.
 *
 * @param ip IP address of the Home Assistant instance
 * @param automationId Entity ID of the automation to trigger (e.g., "automation.movie_mode")
 */
data class HomeAssistantDevice(
    val ip: String,
    val automationId: String,
) : TheaterDevice() {
    override fun setup(http: TheaterHttpClient) {
        logger.info("Triggering Home Assistant automation at {}: {}", ip, automationId)

        val url = "http://$ip:8123/api/services/automation/trigger"
        val body = MediaJson.mapper.writeValueAsString(mapOf("entity_id" to automationId))

        http.sendAndCheck(url, body, "Home Assistant")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(HomeAssistantDevice::class.java)
    }
}
