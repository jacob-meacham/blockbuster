package com.blockbuster.plugin

import com.blockbuster.media.MediaStore
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory

/**
 * Plugin definition data class
 */
data class PluginDefinition(
    val type: String,
    val config: Map<String, Any> = emptyMap()
)

/**
 * Factory for creating media plugins based on configuration
 */
class PluginFactory(
    private val mediaStore: MediaStore,
    private val httpClient: OkHttpClient = OkHttpClient()
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Create a plugin based on the plugin definition
     */
    fun createPlugin(definition: PluginDefinition): MediaPlugin<*> {
        return when (definition.type.lowercase()) {
            "roku" -> createRokuPlugin(definition)
            "spotify" -> createSpotifyPlugin(definition)
            else -> throw IllegalArgumentException("Unknown plugin type: ${definition.type}")
        }
    }

    /**
     * Create a Roku plugin from configuration
     */
    private fun createRokuPlugin(definition: PluginDefinition): RokuPlugin {
        val config = definition.config

        val deviceIp = config["deviceIp"] as? String
            ?: throw IllegalArgumentException("Roku plugin requires 'deviceIp' configuration")

        val deviceName = config["deviceName"] as? String ?: "Roku Device"

        logger.info("Creating Roku plugin: type=${definition.type}, ip=$deviceIp, deviceName=$deviceName")

        return RokuPlugin(
            deviceIp = deviceIp,
            deviceName = deviceName,
            mediaStore = mediaStore,
            httpClient = httpClient
        )
    }

    /**
     * Create a Spotify plugin from configuration (placeholder for future implementation)
     */
    private fun createSpotifyPlugin(definition: PluginDefinition): MediaPlugin<*> {
        val config = definition.config

        val clientId = config["clientId"] as? String
            ?: throw IllegalArgumentException("Spotify plugin requires 'clientId' configuration")

        val clientSecret = config["clientSecret"] as? String
            ?: throw IllegalArgumentException("Spotify plugin requires 'clientSecret' configuration")

        logger.info("Creating Spotify plugin: type=${definition.type}")

        // TODO: Implement Spotify plugin
        throw NotImplementedError("Spotify plugin not yet implemented")
    }
}
