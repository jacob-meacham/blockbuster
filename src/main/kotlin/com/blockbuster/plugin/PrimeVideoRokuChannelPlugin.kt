package com.blockbuster.plugin

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
class PrimeVideoRokuChannelPlugin : RokuChannelPlugin {

    override fun getChannelId(): String = "13"

    override fun getChannelName(): String = "Prime Video"

    override fun buildPlaybackCommand(content: RokuMediaContent, rokuDeviceIp: String): RokuPlaybackCommand {
        val asin = content.contentId
        val mediaType = content.mediaType?.lowercase() ?: "movie"

        return RokuPlaybackCommand.ActionSequence(
            listOf(
                RokuAction.Launch(
                    channelId = getChannelId(),
                    params = "contentId=$asin&mediaType=$mediaType"
                ),
                RokuAction.Wait(2000),  // Wait for profile selection screen
                RokuAction.Press(RokuKey.SELECT, 1)  // Select default profile
            )
        )
    }

    override fun search(query: String): List<RokuMediaContent> {
        // Prime Video doesn't provide a public search API
        // Return instructions for manual content ID discovery
        return listOf(
            RokuMediaContent(
                channelName = getChannelName(),
                channelId = getChannelId(),
                contentId = "MANUAL_SEARCH_REQUIRED",
                title = "Manual Search: How to find Prime Video content IDs",
                mediaType = "instruction",
                metadata = mapOf(
                    "instructions" to """
                        Amazon Prime Video does not provide a public search API.

                        To find content IDs manually:

                        1. Open Prime Video in your web browser
                        2. Search for and navigate to the content you want
                        3. Look at the URL in your browser's address bar
                        4. Extract the ASIN (Amazon Standard Identification Number)

                        ASINs typically start with "B0" followed by alphanumeric characters.

                        Examples:
                        • Movie: B0DKTFF815
                          - mediaType: movie
                          - Behavior: Plays the movie after profile selection

                        • Series: B0FQM41JFJ
                          - mediaType: series
                          - Behavior: Automatically plays S1E1 after profile selection

                        You can often find the ASIN in the product page URL:
                        • URL: https://www.amazon.com/dp/B0DKTFF815
                        • ASIN: B0DKTFF815

                        Use the ASIN as contentId when adding to your NFC library.
                        Set mediaType to "movie" or "series" as appropriate.
                    """.trimIndent(),
                    "searchQuery" to query
                )
            )
        )
    }
}
