package com.blockbuster.plugin

/**
 * Core interface that all media plugins must implement.
 * Provides a simple contract for media playback and plugin identification.
 */
interface MediaPlugin<C> {
    
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

    /**
     * Search for media items for this plugin, returning strongly typed results.
     */
    @Throws(PluginException::class)
    fun search(query: String, options: Map<String, Any> = emptyMap()): List<SearchResult<C>>
}

/**
 * Exception thrown when a plugin operation fails
 */
class PluginException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Generic search result wrapper for plugins.
 * - title: human-readable title
 * - url: deep link to media within provider (if applicable)
 * - mediaUrl: direct media URL/URI if available (optional)
 * - content: strongly typed plugin-specific content object used by our system
 */
data class SearchResult<T>(
    val title: String,
    val url: String? = null,
    val mediaUrl: String? = null,
    val content: T
)
