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
     * Builds the playback command for this channel.
     * Returns either a deep link or an action sequence.
     * Channel plugins do not know about device addressing — RokuPlugin resolves the IP.
     */
    fun buildPlaybackCommand(content: RokuMediaContent): RokuPlaybackCommand

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
     * Plugins can use the optional [title] and [description] from search results
     * to make filtering decisions (e.g., Prime Video only extracts amazon.com
     * URLs that contain "| Prime Video" in the title).
     *
     * Default implementation returns null (e.g., Emby doesn't need URL extraction).
     *
     * @param url The URL to extract content from
     * @param title Optional title from search result for filtering
     * @param description Optional description from search result for filtering
     * @return RokuMediaContent if extraction successful, null otherwise
     */
    fun extractFromUrl(
        url: String,
        title: String? = null,
        description: String? = null,
    ): RokuMediaContent? = null
}

/**
 * Represents a command to play content on Roku
 */
sealed class RokuPlaybackCommand {
    /**
     * Deep link that can be sent to Roku ECP.
     * RokuPlugin resolves channelId + params into a full URL using the device IP.
     */
    data class DeepLink(val channelId: String, val params: String) : RokuPlaybackCommand()

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
 * Roku remote control keys, each carrying its ECP protocol name.
 */
enum class RokuKey(val ecpName: String) {
    HOME("Home"),
    UP("Up"),
    DOWN("Down"),
    LEFT("Left"),
    RIGHT("Right"),
    SELECT("Select"),
    BACK("Back"),
    BACKSPACE("Backspace"),
    PLAY("Play"),
    PAUSE("Pause"),
    REV("Rev"),
    FWD("Fwd"),
    INSTANT_REPLAY("InstantReplay"),
    INFO("Info"),
    SEARCH("Search"),
}
