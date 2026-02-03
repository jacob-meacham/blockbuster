package com.blockbuster.theater

/**
 * Represents different types of theater devices that can be controlled for media playback setup.
 */
sealed class TheaterDevice {
    /**
     * Harmony Hub for controlling home theater equipment.
     *
     * @param ip IP address of the Harmony Hub
     * @param activityId ID of the activity to start
     * @param delayMs Delay in milliseconds after starting activity (default: 5000ms)
     */
    data class HarmonyHub(
        val ip: String,
        val activityId: String,
        val delayMs: Long = 5000
    ) : TheaterDevice() {
        init {
            require(ip.isNotBlank()) { "HarmonyHub ip must not be blank" }
            require(activityId.isNotBlank()) { "HarmonyHub activityId must not be blank" }
            require(delayMs in 0..30000) { "HarmonyHub delayMs must be between 0 and 30000, was $delayMs" }
        }
    }

    /**
     * Home Assistant for triggering automations.
     *
     * @param ip IP address of the Home Assistant instance
     * @param automationId Entity ID of the automation to trigger (e.g., "automation.movie_mode")
     */
    data class HomeAssistant(
        val ip: String,
        val automationId: String
    ) : TheaterDevice()

    /**
     * Roku device for sending remote control commands.
     *
     * @param ip IP address of the Roku device
     */
    data class Roku(
        val ip: String
    ) : TheaterDevice()

    /**
     * No theater setup - playback only.
     */
    object None : TheaterDevice()
}
