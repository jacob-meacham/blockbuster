package com.blockbuster.plugin.roku

/**
 * Netflix channel plugin for Roku.
 *
 * URL extraction and playback (channel 12, `/watch/` → movie, `/title/` → series,
 * PLAY after launch) are handled by the generated [com.blockbuster.plugin.roku.ecp.RokuEcp]
 * core via [StreamingRokuChannelPlugin]. This class supplies only channel identity.
 */
class NetflixRokuChannelPlugin : StreamingRokuChannelPlugin() {
    override fun getChannelId(): String = "12"

    override fun getChannelName(): String = "Netflix"

    override fun getPublicSearchDomain(): String = "netflix.com"
}
