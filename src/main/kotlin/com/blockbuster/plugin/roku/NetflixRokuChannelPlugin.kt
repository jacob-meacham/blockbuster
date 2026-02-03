package com.blockbuster.plugin.roku

import com.blockbuster.media.RokuMediaContent

/**
 * Netflix channel plugin for Roku.
 *
 * Uses standard Roku ECP deep linking with hybrid ActionSequence (PLAY press required).
 *
 * Format: http://<roku-ip>:8060/launch/12?contentId=<id>&mediaType=<type>
 * - contentId: Numeric ID from Netflix URLs (e.g., 81444554)
 * - mediaType: "movie" or "episode"
 *
 * Workflow:
 * 1. Deep link launches Netflix to content selection page
 * 2. PLAY press starts playback
 */
class NetflixRokuChannelPlugin : StreamingRokuChannelPlugin() {

    override fun getChannelId(): String = "12"
    override fun getChannelName(): String = "Netflix"
    override fun getPublicSearchDomain(): String = "netflix.com"
    override fun getSearchUrl(): String = "https://www.netflix.com/search"
    override val urlPattern = Regex("""netflix\.com/(?:watch|title)/(\d+)""")
    override val defaultTitle = "Netflix Content"
    override val postLaunchKey = RokuKey.PLAY

    override fun buildContentFromMatch(match: MatchResult, url: String): RokuMediaContent {
        val contentId = match.groupValues[1]
        val mediaType = if (url.contains("/watch/")) "movie" else "series"

        return RokuMediaContent(
            channelName = getChannelName(),
            channelId = getChannelId(),
            contentId = contentId,
            title = defaultTitle,
            mediaType = mediaType
        )
    }
}
