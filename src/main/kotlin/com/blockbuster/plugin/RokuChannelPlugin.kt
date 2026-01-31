package com.blockbuster.plugin

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
     * Builds the playback command for this channel.
     * Returns either a deep link URL or an action sequence.
     */
    fun buildPlaybackCommand(content: RokuMediaContent, rokuDeviceIp: String): RokuPlaybackCommand

    /**
     * Optional: Search support for this channel
     * Returns null if channel doesn't support search
     */
    fun search(query: String): List<RokuMediaContent>? = null
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
    data class Launch(val channelId: String) : RokuAction()
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
