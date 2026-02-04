package com.blockbuster.theater

import com.blockbuster.plugin.roku.RokuPlugin
import org.slf4j.LoggerFactory

/**
 * Handler for Roku device theater setup.
 *
 * Sends a Home keypress to the Roku device to ensure it's on the home screen
 * before media playback begins.
 */
class RokuTheaterHandler(
    private val device: TheaterDevice.Roku,
    private val http: TheaterHttpClient,
) : TheaterDeviceHandler {
    private val logger = LoggerFactory.getLogger(RokuTheaterHandler::class.java)

    override fun setup() {
        logger.info("Sending Home keypress to Roku at {}", device.ip)

        val url = "http://${device.ip}:${RokuPlugin.ECP_PORT}/keypress/Home"

        http.sendAndCheck(url, null, "Roku")
    }
}
