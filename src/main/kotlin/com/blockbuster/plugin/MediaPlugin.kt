package com.blockbuster.plugin

/**
 * Core interface that all media plugins must implement.
 * Provides a simple contract for media playback and plugin identification.
 */
interface MediaPlugin {
    
    /**
     * Get the unique name of this plugin
     */
    fun getPluginName(): String
    
    /**
     * Get a human-readable description of this plugin
     */
    fun getDescription(): String

    /**
     * Play media content with the given ID and options
     *
     * @param contentId The content identifier (plugin-specific format)
     * @param options Additional options for playback (plugin-specific)
     * @throws PluginException if playback fails
     */
    @Throws(PluginException::class)
    fun play(contentId: String, options: Map<String, Any>)
}

/**
 * Exception thrown when a plugin operation fails
 */
class PluginException(message: String, cause: Throwable? = null) : Exception(message, cause)
