package com.blockbuster.plugin.roku

import com.blockbuster.media.RokuMediaContent

/**
 * Abstract base class for streaming service Roku channel plugins (Disney+, Netflix, HBO Max, Prime Video).
 *
 * These channels all share the same pattern:
 * - ActionSequence playback: Launch → Wait → keypress (SELECT or PLAY)
 * - No public search API (return empty results)
 * - URL extraction via regex pattern
 *
 * Subclasses define channel metadata via interface method overrides, plus:
 * - [urlPattern]: Regex to extract content IDs from URLs
 * - [defaultTitle]: Fallback title for extracted content
 * - [postLaunchKey]: Key to press after launch (SELECT for profile selection, PLAY for Netflix)
 *
 * Optionally override [buildContentFromMatch] for custom media type logic (e.g., Netflix).
 */
abstract class StreamingRokuChannelPlugin : RokuChannelPlugin {

    abstract val urlPattern: Regex
    abstract val defaultTitle: String
    abstract val postLaunchKey: RokuKey

    override fun buildPlaybackCommand(content: RokuMediaContent, rokuDeviceIp: String): RokuPlaybackCommand {
        val contentId = content.contentId
        val mediaType = content.mediaType?.lowercase() ?: "movie"

        return RokuPlaybackCommand.ActionSequence(
            listOf(
                RokuAction.Launch(
                    channelId = getChannelId(),
                    params = "contentId=$contentId&mediaType=$mediaType"
                ),
                RokuAction.Wait(2000),
                RokuAction.Press(postLaunchKey, 1)
            )
        )
    }

    override fun search(query: String): List<RokuMediaContent> = emptyList()

    override fun extractFromUrl(url: String): RokuMediaContent? {
        val match = urlPattern.find(url) ?: return null
        return buildContentFromMatch(match, url)
    }

    /**
     * Builds a [RokuMediaContent] from a URL regex match.
     * Override for custom media type logic (e.g., Netflix watch vs title).
     */
    protected open fun buildContentFromMatch(match: MatchResult, url: String): RokuMediaContent {
        return RokuMediaContent(
            channelName = getChannelName(),
            channelId = getChannelId(),
            contentId = match.groupValues[1],
            title = defaultTitle,
            mediaType = "movie"
        )
    }
}
