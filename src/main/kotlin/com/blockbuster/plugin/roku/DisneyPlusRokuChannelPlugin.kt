package com.blockbuster.plugin.roku

/**
 * Disney+ channel plugin for Roku.
 *
 * Uses standard Roku ECP deep linking with hybrid ActionSequence (profile selection required).
 *
 * Format: http://<roku-ip>:8060/launch/291097?contentId=<id>&mediaType=<type>
 * - contentId: UUID from Disney+ URLs (e.g., f63db666-b097-4c61-99c1-b778de2d4ae1)
 * - mediaType: "movie", "episode", "series", "season"
 *
 * Supported URL formats:
 * - /play/{uuid} (e.g., /play/f63db666-b097-4c61-99c1-b778de2d4ae1)
 * - /video/{uuid}
 * - /browse/entity-{uuid} (e.g., /browse/entity-998ad7ff-51b8-47ec-b571-85152ba5d2ce)
 *
 * Workflow:
 * 1. Deep link launches Disney+ to content
 * 2. Profile selection screen appears
 * 3. SELECT press chooses default profile
 * 4. Content auto-plays
 */
class DisneyPlusRokuChannelPlugin : StreamingRokuChannelPlugin() {
    override fun getChannelId(): String = "291097"

    override fun getChannelName(): String = "Disney+"

    override fun getPublicSearchDomain(): String = "disneyplus.com"

    override val urlPattern = Regex("""disneyplus\.com/(?:(?:play|video)/|browse/entity-)([a-f0-9-]+)""")
    override val defaultTitle = "Disney+ Content"
    override val postLaunchKey = RokuKey.SELECT
}
