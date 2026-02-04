package com.blockbuster.theater

import com.blockbuster.media.MediaJson
import org.slf4j.LoggerFactory

/**
 * Harmony Hub for controlling home theater equipment.
 *
 * Sends a POST to the Harmony Hub API to start the specified activity,
 * then waits for the configured delay to allow equipment to power on.
 *
 * @param ip IP address of the Harmony Hub
 * @param activityId ID of the activity to start
 * @param delayMs Delay in milliseconds after starting activity (default: 5000ms)
 */
data class HarmonyHubDevice(
    val ip: String,
    val activityId: String,
    val delayMs: Long = 5000,
) : TheaterDevice() {
    init {
        require(ip.isNotBlank()) { "HarmonyHub ip must not be blank" }
        require(activityId.isNotBlank()) { "HarmonyHub activityId must not be blank" }
        require(delayMs in 0..30000) { "HarmonyHub delayMs must be between 0 and 30000, was $delayMs" }
    }

    override fun setup(http: TheaterHttpClient) {
        logger.info("Setting up Harmony Hub at {}, activity {}", ip, activityId)

        val url = "http://$ip:8088/start"
        val body = MediaJson.mapper.writeValueAsString(mapOf("activityId" to activityId))

        http.sendAndCheck(url, body, "Harmony Hub")

        if (delayMs > 0) {
            try {
                Thread.sleep(delayMs)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                logger.warn("Harmony Hub delay interrupted")
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(HarmonyHubDevice::class.java)
    }
}
