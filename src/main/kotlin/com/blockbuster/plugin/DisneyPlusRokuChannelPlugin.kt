package com.blockbuster.plugin

import com.blockbuster.media.RokuMediaContent

/**
 * Disney+ channel plugin for Roku.
 *
 * Uses standard Roku ECP deep linking with hybrid ActionSequence (profile selection required).
 *
 * Format: http://<roku-ip>:8060/launch/291097?contentId=<id>&mediaType=<type>
 * - contentId: UUID from Disney+ URLs (e.g., f63db666-b097-4c61-99c1-b778de2d4ae1)
 * - mediaType: "movie", "episode", "series", "season"
 *
 * Workflow:
 * 1. Deep link launches Disney+ to content
 * 2. Profile selection screen appears
 * 3. SELECT press chooses default profile
 * 4. Content auto-plays
 */
class DisneyPlusRokuChannelPlugin : RokuChannelPlugin {

    override fun getChannelId(): String = "291097"

    override fun getChannelName(): String = "Disney+"

    override fun buildPlaybackCommand(content: RokuMediaContent, rokuDeviceIp: String): RokuPlaybackCommand {
        val contentId = content.contentId
        val mediaType = content.mediaType?.lowercase() ?: "movie"

        return RokuPlaybackCommand.ActionSequence(
            listOf(
                RokuAction.Launch(
                    channelId = getChannelId(),
                    params = "contentId=$contentId&mediaType=$mediaType"
                ),
                RokuAction.Wait(2000),  // Wait for profile selection screen
                RokuAction.Press(RokuKey.SELECT, 1)  // Select default profile
            )
        )
    }

    override fun search(query: String): List<RokuMediaContent> {
        // Disney+ doesn't provide a public search API
        // Return instructions for manual content ID discovery
        return listOf(
            RokuMediaContent(
                channelName = getChannelName(),
                channelId = getChannelId(),
                contentId = "MANUAL_SEARCH_REQUIRED",
                title = "Manual Search: How to find Disney+ content IDs",
                mediaType = "instruction",
                metadata = mapOf(
                    "instructions" to """
                        Disney+ does not provide a public search API.

                        To find content IDs manually:

                        1. Open Disney+ in your web browser
                        2. Search for and navigate to the content you want
                        3. Look at the URL in your browser's address bar
                        4. Extract the UUID from the URL

                        Examples:
                        • URL: https://www.disneyplus.com/play/f63db666-b097-4c61-99c1-b778de2d4ae1
                        • Content ID: f63db666-b097-4c61-99c1-b778de2d4ae1

                        • URL: https://www.disneyplus.com/series/the-mandalorian/3jLIGMDYINqD
                        • Content ID: 3jLIGMDYINqD

                        Use this UUID as the contentId when adding to your NFC library.
                        Set mediaType to "movie", "episode", or "series" as appropriate.
                    """.trimIndent(),
                    "searchQuery" to query
                )
            )
        )
    }
}
