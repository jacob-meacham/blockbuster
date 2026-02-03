package com.blockbuster.plugin.roku

import com.blockbuster.media.RokuMediaContent

/**
 * Channel-specific plugin for Roku that knows how to build playback commands
 * for a specific channel (Emby, Netflix, HBO, etc.)
 *
 * This plugin handles the channel-specific logic of how to launch content,
 * while RokuPlugin handles the actual Roku device communication.
 */
interface RokuChannelPlugin {
    /**
     * Returns the Roku channel ID (e.g., "44191" for Emby)
     */
    fun getChannelId(): String

    /**
     * Returns the human-readable channel name (e.g., "Emby", "Netflix")
     */
    fun getChannelName(): String

    /**
     * Returns the public search domain for this channel (e.g., "netflix.com", "disneyplus.com").
     * Used for building site: filters in web search APIs.
     *
     * Returns empty string for private/local servers (e.g., Emby on localhost)
     * that should not be included in public web searches.
     */
    fun getPublicSearchDomain(): String

    /**
     * Returns the search URL for this channel (e.g., "https://netflix.com/search").
     * Used for manual search instructions when API search isn't available.
     */
    fun getSearchUrl(): String

    /**
     * Builds the playback command for this channel.
     * Returns either a deep link URL or an action sequence.
     */
    fun buildPlaybackCommand(content: RokuMediaContent, rokuDeviceIp: String): RokuPlaybackCommand

    /**
     * Search for content on this channel.
     *
     * Channels with API access return actual search results.
     * Channels without API access return manual search instructions.
     *
     * Never returns null - always provides either results or instructions.
     */
    fun search(query: String): List<RokuMediaContent> = emptyList()

    /**
     * Extracts RokuMediaContent from a channel-specific URL.
     *
     * Default implementation returns null (e.g., Emby doesn't need URL extraction).
     *
     * @param url The URL to extract content from
     * @return RokuMediaContent if extraction successful, null otherwise
     */
    fun extractFromUrl(url: String): RokuMediaContent? = null
}

/**
 * Represents a command to play content on Roku
 */
sealed class RokuPlaybackCommand {
    /**
     * Deep link URL that can be sent directly to Roku ECP
     * Example: http://192.168.1.252:8060/launch/44191?Command=PlayNow&ItemIds=541
     */
    data class DeepLink(val url: String) : RokuPlaybackCommand()

    /**
     * Action sequence to navigate the channel UI
     * Used when deep linking isn't supported
     */
    data class ActionSequence(val actions: List<RokuAction>) : RokuPlaybackCommand()
}

/**
 * Roku action for programmatic UI navigation
 */
sealed class RokuAction {
    data class Launch(val channelId: String, val params: String = "") : RokuAction()
    data class Press(val key: RokuKey, val count: Int = 1) : RokuAction()
    data class Type(val text: String) : RokuAction()
    data class Wait(val milliseconds: Long) : RokuAction()
}

/**
 * Roku remote control keys
 */
enum class RokuKey {
    HOME, UP, DOWN, LEFT, RIGHT, SELECT, BACK, BACKSPACE,
    PLAY, PAUSE, REV, FWD,
    INSTANT_REPLAY, INFO, SEARCH
}
