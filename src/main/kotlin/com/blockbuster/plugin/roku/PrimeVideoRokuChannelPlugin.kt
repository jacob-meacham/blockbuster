package com.blockbuster.plugin.roku

import com.blockbuster.media.RokuMediaContent

/**
 * Amazon Prime Video channel plugin for Roku.
 *
 * Uses standard Roku ECP deep linking with hybrid ActionSequence (profile selection required).
 *
 * Format: http://<roku-ip>:8060/launch/13?contentId=<asin>&mediaType=<type>
 * - contentId: Amazon ASIN format (starts with "B0...", e.g., B0DKTFF815)
 * - mediaType: "movie" or "series"
 *
 * Special behavior:
 * - For series: Automatically plays S1E1
 *
 * Workflow:
 * 1. Deep link launches Prime Video to content
 * 2. Profile selection screen appears
 * 3. SELECT press chooses default profile
 * 4. Content auto-plays
 */
class PrimeVideoRokuChannelPlugin : StreamingRokuChannelPlugin() {
    override fun getChannelId(): String = "13"

    override fun getChannelName(): String = "Prime Video"

    override fun getPublicSearchDomain(): String = "amazon.com"

    override val urlPattern = Regex("""(?:amazon\.com|primevideo\.com)/.*?/([B][A-Z0-9]{9})""")
    override val defaultTitle = "Prime Video Content"
    override val postLaunchKey = RokuKey.SELECT

    /**
     * Only extracts content if the URL matches AND (when title is provided) indicates Prime Video.
     *
     * Amazon URLs can be for physical products, Kindle books, etc. When called from search
     * results (title provided), we filter to only accept results where the title contains
     * "| Prime Video" (Amazon's standard format for streaming content).
     *
     * Direct URL extraction (title=null) bypasses this filter since the user explicitly
     * provided the URL.
     */
    override fun extractFromUrl(
        url: String,
        title: String?,
        description: String?,
    ): RokuMediaContent? {
        // Only filter amazon.com URLs when title is provided but doesn't indicate Prime Video
        if ("amazon.com" in url && title != null && !title.contains("| Prime Video")) {
            return null
        }
        return super.extractFromUrl(url, title, description)
    }
}
