package com.blockbuster.plugin

import org.slf4j.LoggerFactory

/**
 * Manager for media plugins. Maintains a registry of plugins by name
 * and provides methods to interact with them.
 */
class MediaPluginManager(private val plugins: List<MediaPlugin<*>>) {

    private val logger = LoggerFactory.getLogger(javaClass)
    private val pluginRegistry: Map<String, MediaPlugin<*>>

    init {
        pluginRegistry = plugins.associateBy { it.getPluginName() }
        logger.info("MediaPluginManager initialized with ${plugins.size} plugins: ${pluginRegistry.keys}")
    }

    /**
     * Get a plugin by name
     */
    fun getPlugin(name: String): MediaPlugin<*>? = pluginRegistry[name]

    /**
     * Get all registered plugins
     */
    fun getAllPlugins(): List<MediaPlugin<*>> = plugins

    /**
     * Play content using the specified plugin
     */
    @Throws(PluginException::class)
    fun play(pluginName: String, contentId: String, options: Map<String, Any>) {
        val plugin = pluginRegistry[pluginName]
            ?: throw PluginException("Plugin '$pluginName' not found")

        plugin.play(contentId, options)
    }
}
