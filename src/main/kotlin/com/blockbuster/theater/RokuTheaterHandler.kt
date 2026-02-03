package com.blockbuster.theater

import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpRequest

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

        val request =
            HttpRequest.newBuilder()
                .uri(URI.create("http://${device.ip}:${com.blockbuster.plugin.roku.RokuPlugin.ECP_PORT}/keypress/Home"))
                .POST(HttpRequest.BodyPublishers.ofString(""))
                .build()

        http.sendAndCheck(request, "Roku")
    }
}
