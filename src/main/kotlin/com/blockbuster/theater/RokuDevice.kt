package com.blockbuster.theater

import com.blockbuster.plugin.roku.RokuPlugin
import org.slf4j.LoggerFactory

/**
 * Roku device for sending remote control commands.
 *
 * Sends a Home keypress to the Roku device to ensure it's on the home screen
 * before media playback begins.
 *
 * @param ip IP address of the Roku device
 */
data class RokuDevice(
    val ip: String,
) : TheaterDevice() {
    override fun setup(http: TheaterHttpClient) {
        logger.info("Sending Home keypress to Roku at {}", ip)

        val url = "http://$ip:${RokuPlugin.ECP_PORT}/keypress/Home"

        http.sendAndCheck(url, null, "Roku")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(RokuDevice::class.java)
    }
}
