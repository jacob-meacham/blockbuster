package com.blockbuster.theater

/**
 * Exception thrown when theater device setup fails.
 *
 * @param deviceType Human-readable device type (e.g., "Harmony Hub", "Roku")
 */
class TheaterSetupException(
    val deviceType: String,
    message: String,
    cause: Throwable? = null,
) : RuntimeException("$deviceType setup failed: $message", cause)

/**
 * Represents different types of theater devices that can be controlled for media playback setup.
 *
 * Each variant knows how to set itself up given a [TheaterHttpClient].
 */
sealed class TheaterDevice {
    /**
     * Execute the setup procedure for this theater device.
     *
     * @param http The HTTP client used to send setup commands
     * @throws TheaterSetupException if the device setup fails
     */
    abstract fun setup(http: TheaterHttpClient)

    /**
     * No theater setup - playback only.
     */
    object None : TheaterDevice() {
        override fun setup(http: TheaterHttpClient) {
            // No-op: no theater device to set up
        }
    }
}
