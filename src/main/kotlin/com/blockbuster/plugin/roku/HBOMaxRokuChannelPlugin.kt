package com.blockbuster.plugin.roku

/**
 * HBO Max channel plugin for Roku.
 *
 * Uses standard Roku ECP deep linking with hybrid ActionSequence (profile selection required).
 *
 * Format: http://<roku-ip>:8060/launch/61322?contentId=<id>&mediaType=<type>
 * - contentId: UUID from HBO Max video URLs (first ID from /video/watch/{id1}/{id2})
 * - mediaType: "movie" or "episode"
 *
 * Important: Use first ID from /video/watch/{id1}/{id2} URLs, NOT from /movie/{id} URLs
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
    override fun getPublicSearchDomain(): String = "max.com"
    override fun getSearchUrl(): String = "https://www.max.com/search"
    override val urlPattern = Regex("""(?:max\.com|hbomax\.com)/(?:video/watch|play)/([^/?]+)""")
    override val defaultTitle = "HBO Max Content"
    override val postLaunchKey = RokuKey.SELECT
}
