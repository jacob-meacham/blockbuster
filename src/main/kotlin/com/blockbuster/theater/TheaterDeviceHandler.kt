package com.blockbuster.theater

/**
 * Handler for setting up a specific theater device before media playback.
 *
 * Each implementation encapsulates the HTTP request construction and
 * any post-setup behavior (e.g., delays) for a single device type.
 */
fun interface TheaterDeviceHandler {
    /**
     * Execute the setup procedure for this theater device.
     *
     * @throws TheaterSetupException if the device setup fails
     */
    fun setup()
}

/**
 * Exception thrown when theater device setup fails.
 *
 * @param deviceType Human-readable device type (e.g., "Harmony Hub", "Roku")
 */
class TheaterSetupException(
    val deviceType: String,
    message: String,
    cause: Throwable? = null
) : RuntimeException("$deviceType setup failed: $message", cause)

/**
 * Creates a [TheaterDeviceHandler] for the given [TheaterDevice] configuration.
 *
 * Returns null for [TheaterDevice.None] (no setup needed).
 *
 * This function contains the exhaustive `when` on the sealed class,
 * so the Kotlin compiler will produce a build error if a new variant
 * is added to [TheaterDevice] without updating this mapping.
 */
fun createTheaterHandler(device: TheaterDevice, http: TheaterHttpClient): TheaterDeviceHandler? {
    return when (device) {
        is TheaterDevice.HarmonyHub -> HarmonyHubHandler(device, http)
        is TheaterDevice.HomeAssistant -> HomeAssistantHandler(device, http)
        is TheaterDevice.Roku -> RokuTheaterHandler(device, http)
        is TheaterDevice.None -> null
    }
}
