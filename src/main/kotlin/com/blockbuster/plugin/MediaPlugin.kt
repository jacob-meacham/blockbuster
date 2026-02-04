package com.blockbuster.plugin

import com.blockbuster.media.MediaContent
import com.blockbuster.media.MediaContentParser

/**
 * Core interface that all media plugins must implement.
 * Provides a simple contract for media playback and plugin identification.
 */
interface MediaPlugin<C : MediaContent> {
    /**
     * Get the unique name of this plugin
     */
    fun getPluginName(): String

    /**
     * Get a human-readable description of this plugin
     */
    fun getDescription(): String

    /**
     * Play media content with the given ID.
     *
     * @param contentId The content identifier (plugin-specific format)
     * @throws PluginException if playback fails
     */
    @Throws(PluginException::class)
    fun play(contentId: String)

    /**
     * Search for media items for this plugin, returning strongly typed results.
     */
    @Throws(PluginException::class)
    fun search(
        query: String,
        options: SearchOptions = SearchOptions(),
    ): List<SearchResult<C>>

    /**
     * Provide a parser capable of converting JSON for this plugin into the strongly-typed content.
     */
    fun getContentParser(): MediaContentParser<C>
}

/**
 * Exception thrown when a plugin operation fails
 */
class PluginException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Generic search result wrapper for plugins.
 *
 * Plugin-specific data lives on [content]. Only generic display fields belong here.
 * Plugins set [dedupKey] so the resource layer can deduplicate across sources
 * without knowing the content type (e.g. Roku uses "channelId-contentId").
 */
data class SearchResult<T>(
    val title: String,
    val url: String? = null,
    val mediaUrl: String? = null,
    val content: T,
    val source: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val dedupKey: String? = null,
)

/**
 * Implemented by plugins that expose channel info (e.g., Roku channel plugins).
 * Lets the resource layer query channel metadata without runtime type-casting.
 */
interface ChannelInfoProvider {
    fun getChannelInfo(): List<ChannelInfoItem>
}

data class ChannelInfoItem(
    val channelId: String,
    val channelName: String,
    val searchUrl: String,
)

/**
 * Immutable registry of plugins keyed by name.
 * Wraps the generic map so Jersey resource constructors don't expose wildcard types.
 */
class PluginRegistry(private val plugins: Map<String, MediaPlugin<*>>) {
    operator fun get(name: String): MediaPlugin<*>? = plugins[name]

    val values: Collection<MediaPlugin<*>> get() = plugins.values

    val keys: Set<String> get() = plugins.keys

    val size: Int get() = plugins.size

    fun isEmpty(): Boolean = plugins.isEmpty()
}
