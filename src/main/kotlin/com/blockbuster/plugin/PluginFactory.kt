package com.blockbuster.plugin

import com.blockbuster.PluginDefinition
import com.blockbuster.media.MediaJson
import com.blockbuster.media.MediaStore
import com.blockbuster.plugin.roku.DisneyPlusRokuChannelPlugin
import com.blockbuster.plugin.roku.EmbyRokuChannelPlugin
import com.blockbuster.plugin.roku.HBOMaxRokuChannelPlugin
import com.blockbuster.plugin.roku.NetflixRokuChannelPlugin
import com.blockbuster.plugin.roku.PrimeVideoRokuChannelPlugin
import com.blockbuster.plugin.roku.RokuChannelPlugin
import com.blockbuster.plugin.roku.RokuPlugin
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory

/**
 * Typed configuration for a Roku plugin instance.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class RokuPluginConfig(
    val deviceIp: String? = null,
    val deviceName: String = "Roku Device",
    val braveSearchApiKey: String? = null,
    val channels: List<RokuChannelConfig> = emptyList(),
)

/**
 * Typed configuration for a Roku channel plugin.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class RokuChannelConfig(
    val type: String = "",
    val config: Map<String, Any> = emptyMap(),
)

/**
 * Typed configuration for the Emby channel plugin.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class EmbyChannelConfig(
    val serverUrl: String? = null,
    val apiKey: String? = null,
    val userId: String? = null,
)

/**
 * Factory for creating media plugins based on configuration
 */
class PluginFactory(
    private val mediaStore: MediaStore,
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val braveSearchApiKey: String? = null,
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
     * Create a Roku plugin from typed configuration
     */
    private fun createRokuPlugin(definition: PluginDefinition): RokuPlugin {
        val config =
            try {
                MediaJson.mapper.convertValue(definition.config, RokuPluginConfig::class.java)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid Roku plugin configuration: ${e.message}", e)
            }

        val deviceIp =
            config.deviceIp
                ?: throw IllegalArgumentException("Roku plugin requires 'deviceIp' configuration")

        // Plugin config overrides factory-level key
        val resolvedBraveKey = config.braveSearchApiKey ?: this.braveSearchApiKey

        val channelPlugins = createRokuChannelPlugins(config.channels)

        logger.info(
            "Creating Roku plugin: type={}, ip={}, deviceName={}, channels={}, braveSearch={}",
            definition.type,
            deviceIp,
            config.deviceName,
            channelPlugins.size,
            !resolvedBraveKey.isNullOrBlank(),
        )

        return RokuPlugin(
            deviceIp = deviceIp,
            deviceName = config.deviceName,
            mediaStore = mediaStore,
            httpClient = httpClient,
            channelPlugins = channelPlugins,
            braveSearchApiKey = resolvedBraveKey,
        )
    }

    /**
     * Create Roku channel plugins from typed configuration
     */
    private fun createRokuChannelPlugins(channels: List<RokuChannelConfig>): Map<String, RokuChannelPlugin> {
        val channelPlugins = mutableMapOf<String, RokuChannelPlugin>()

        for (channelConfig in channels) {
            val type = channelConfig.type
            if (type.isBlank()) continue

            try {
                val channelPlugin =
                    when (type.lowercase()) {
                        "emby" -> createEmbyChannelPlugin(channelConfig.config)
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
                logger.error("Failed to create channel plugin for type '{}': {}", type, e.message, e)
            }
        }

        return channelPlugins
    }

    /**
     * Create Emby channel plugin from typed configuration
     */
    private fun createEmbyChannelPlugin(rawConfig: Map<String, Any>): RokuChannelPlugin {
        val config =
            try {
                MediaJson.mapper.convertValue(rawConfig, EmbyChannelConfig::class.java)
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid Emby channel configuration: ${e.message}", e)
            }

        val serverUrl =
            config.serverUrl
                ?: throw IllegalArgumentException("Emby channel plugin requires 'serverUrl' configuration")
        val apiKey =
            config.apiKey
                ?: throw IllegalArgumentException("Emby channel plugin requires 'apiKey' configuration")
        val userId =
            config.userId
                ?: throw IllegalArgumentException("Emby channel plugin requires 'userId' configuration")

        return EmbyRokuChannelPlugin(
            embyServerUrl = serverUrl,
            embyApiKey = apiKey,
            embyUserId = userId,
            httpClient = httpClient,
            objectMapper = MediaJson.mapper,
        )
    }
}
