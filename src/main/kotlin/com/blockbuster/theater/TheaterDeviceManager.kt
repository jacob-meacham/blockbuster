package com.blockbuster.theater

import com.blockbuster.BlockbusterConfiguration
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Manages theater device setup for media playback.
 *
 * Triggers configured theater devices (Harmony Hub, Home Assistant, etc.)
 * before media playback begins.
 */
class TheaterDeviceManager(
    private val config: BlockbusterConfiguration
) {
    private val logger = LoggerFactory.getLogger(TheaterDeviceManager::class.java)
    private val httpClient = HttpClient.newHttpClient()

    /**
     * Setup theater for the given appliance device ID.
     *
     * @param deviceId The appliance device ID (e.g., "living-room")
     * @throws IllegalArgumentException if deviceId is unknown
     */
    fun setupTheater(deviceId: String) {
        val appliance = config.appliances[deviceId]
            ?: throw IllegalArgumentException("Unknown device: $deviceId")

        when (val theater = appliance.theater) {
            is TheaterDevice.HarmonyHub -> setupHarmonyHub(theater)
            is TheaterDevice.HomeAssistant -> setupHomeAssistant(theater)
            is TheaterDevice.Roku -> setupRoku(theater)
            is TheaterDevice.None -> logger.debug("No theater setup for device: $deviceId")
        }
    }

    private fun setupHarmonyHub(device: TheaterDevice.HarmonyHub) {
        logger.info("Setting up Harmony Hub at ${device.ip}, activity ${device.activityId}")

        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://${device.ip}:8088/start"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(
                """{"activityId":"${device.activityId}"}"""
            ))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        logger.debug("Harmony Hub response: ${response.statusCode()}")

        try {
            Thread.sleep(device.delayMs)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            logger.warn("Harmony Hub delay interrupted")
        }
    }

    private fun setupHomeAssistant(device: TheaterDevice.HomeAssistant) {
        logger.info("Triggering Home Assistant automation at ${device.ip}: ${device.automationId}")

        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://${device.ip}:8123/api/services/automation/trigger"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(
                """{"entity_id":"${device.automationId}"}"""
            ))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        logger.debug("Home Assistant response: ${response.statusCode()}")
    }

    private fun setupRoku(device: TheaterDevice.Roku) {
        logger.info("Sending Home keypress to Roku at ${device.ip}")

        val request = HttpRequest.newBuilder()
            .uri(URI.create("http://${device.ip}:8060/keypress/Home"))
            .POST(HttpRequest.BodyPublishers.ofString(""))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        logger.debug("Roku response: ${response.statusCode()}")
    }
}
