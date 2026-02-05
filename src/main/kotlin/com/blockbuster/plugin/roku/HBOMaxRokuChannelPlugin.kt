package com.blockbuster.plugin.roku

/**
 * HBO Max channel plugin for Roku.
 *
 * Uses standard Roku ECP deep linking with hybrid ActionSequence (profile selection required).
 *
 * Format: http://<roku-ip>:8060/launch/61322?contentId=<id>&mediaType=<type>
 * - contentId: UUID from HBO Max URLs
 * - mediaType: "movie" or "episode"
 *
 * Supported URL formats:
 * - /movies/{slug}/{uuid} (e.g., /movies/howls-moving-castle/7a7a03ca-dd3a-4e62-9e43-e845f338f85e)
 * - /series/{slug}/{uuid}
 * - /video/watch/{uuid}
 * - /play/{uuid}
 *
 * Workflow:
 * 1. Deep link launches HBO Max to content
 * 2. Profile selection screen appears
 * 3. SELECT press chooses default profile
 * 4. Content auto-plays
 */
class HBOMaxRokuChannelPlugin : StreamingRokuChannelPlugin() {
    override fun getChannelId(): String = "61322"

    override fun getChannelName(): String = "HBO Max"

    override fun getPublicSearchDomain(): String = "hbomax.com"

    override val urlPattern =
        Regex(
            """(?:max\.com|hbomax\.com)/""" +
                """(?:(?:movies|series)/[^/]+/|(?:video/watch|play)/)([^/?]+)""",
        )
    override val defaultTitle = "HBO Max Content"
    override val postLaunchKey = RokuKey.SELECT
}
