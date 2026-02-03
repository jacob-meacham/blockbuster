package com.blockbuster.plugin

import com.blockbuster.media.MediaStore
import com.blockbuster.plugin.roku.DisneyPlusRokuChannelPlugin
import com.blockbuster.plugin.roku.EmbyRokuChannelPlugin
import com.blockbuster.plugin.roku.HBOMaxRokuChannelPlugin
import com.blockbuster.plugin.roku.NetflixRokuChannelPlugin
import com.blockbuster.plugin.roku.PrimeVideoRokuChannelPlugin
import com.blockbuster.plugin.roku.RokuChannelPlugin
import com.blockbuster.plugin.roku.RokuPlugin
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory

import com.blockbuster.PluginDefinition

/**
 * Factory for creating media plugins based on configuration
 */
class PluginFactory(
    private val mediaStore: MediaStore,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val braveSearchApiKey: String? = null
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Create a plugin based on the plugin definition
     */
    fun createPlugin(definition: PluginDefinition): MediaPlugin<*> {
        return when (definition.type.lowercase()) {
            "roku" -> createRokuPlugin(definition)
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

        // Optional Brave Search API key for content discovery (plugin config overrides factory-level key)
        val braveSearchApiKey = config["braveSearchApiKey"] as? String ?: this.braveSearchApiKey

        // Create channel plugins from configuration
        val channelPlugins = createRokuChannelPlugins(config)

        logger.info("Creating Roku plugin: type=${definition.type}, ip=$deviceIp, deviceName=$deviceName, channels=${channelPlugins.size}, braveSearch=${!braveSearchApiKey.isNullOrBlank()}")

        return RokuPlugin(
            deviceIp = deviceIp,
            deviceName = deviceName,
            mediaStore = mediaStore,
            httpClient = httpClient,
            channelPlugins = channelPlugins,
            braveSearchApiKey = braveSearchApiKey
        )
    }

    /**
     * Create Roku channel plugins from configuration
     */
    @Suppress("UNCHECKED_CAST")
    private fun createRokuChannelPlugins(config: Map<String, Any>): Map<String, RokuChannelPlugin> {
        val channelsConfig = config["channels"] as? List<Map<String, Any>> ?: return emptyMap()

        val channelPlugins = mutableMapOf<String, RokuChannelPlugin>()
        val objectMapper = ObjectMapper()

        for (channelConfig in channelsConfig) {
            val type = channelConfig["type"] as? String ?: continue
            val enabled = channelConfig["enabled"] as? Boolean ?: true
            if (!enabled) continue

            val channelSpecificConfig = channelConfig["config"] as? Map<String, Any> ?: emptyMap()

            try {
                val channelPlugin = when (type.lowercase()) {
                    "emby" -> createEmbyChannelPlugin(channelSpecificConfig, objectMapper)
                    "disney+", "disneyplus" -> DisneyPlusRokuChannelPlugin()
                    "netflix" -> NetflixRokuChannelPlugin()
                    "hbomax", "hbo max", "hbo" -> HBOMaxRokuChannelPlugin()
                    "primevideo", "prime video", "prime", "amazon" -> PrimeVideoRokuChannelPlugin()
                    else -> {
                        logger.warn("Unknown Roku channel plugin type: {}", type)
                        null
                    }
                }

                if (channelPlugin != null) {
                    channelPlugins[channelPlugin.getChannelId()] = channelPlugin
                    logger.info("Registered Roku channel plugin: {} (ID: {})", channelPlugin.getChannelName(), channelPlugin.getChannelId())
                }
            } catch (e: Exception) {
                logger.error("Failed to create channel plugin for type '{}': {}", type, e.message)
            }
        }

        return channelPlugins
    }

    /**
     * Create Emby channel plugin
     */
    private fun createEmbyChannelPlugin(config: Map<String, Any>, objectMapper: ObjectMapper): RokuChannelPlugin {
        val serverUrl = config["serverUrl"] as? String
            ?: throw IllegalArgumentException("Emby channel plugin requires 'serverUrl' configuration")

        val apiKey = config["apiKey"] as? String
            ?: throw IllegalArgumentException("Emby channel plugin requires 'apiKey' configuration")

        val userId = config["userId"] as? String
            ?: throw IllegalArgumentException("Emby channel plugin requires 'userId' configuration")

        return EmbyRokuChannelPlugin(
            embyServerUrl = serverUrl,
            embyApiKey = apiKey,
            embyUserId = userId,
            httpClient = httpClient,
            objectMapper = objectMapper
        )
    }
}
