package com.blockbuster.plugin.roku

import com.blockbuster.media.RokuMediaContent
import com.blockbuster.plugin.roku.ecp.RokuEcp

/**
 * Abstract base class for streaming service Roku channel plugins (Disney+, Netflix, HBO Max, Prime Video).
 *
 * URL extraction and playback-command building are delegated to [RokuEcp] — the
 * code generated from the `roku-deeplink` spec-library. Subclasses supply only
 * channel identity (id, name, public search domain) and any channel-specific
 * search or filtering behavior; the channel catalog (regex patterns, launch
 * params, post-launch actions) lives in the generated core, not here.
 */
abstract class StreamingRokuChannelPlugin : RokuChannelPlugin {
    override fun buildPlaybackCommand(content: RokuMediaContent): RokuPlaybackCommand =
        RokuEcp.buildPlaybackCommand(content)

    override fun search(query: String): List<RokuMediaContent> = emptyList()

    override fun extractFromUrl(
        url: String,
        title: String?,
        description: String?,
    ): RokuMediaContent? = RokuEcp.convertUrlToEcpCommand(url)?.takeIf { it.channelId == getChannelId() }
}
