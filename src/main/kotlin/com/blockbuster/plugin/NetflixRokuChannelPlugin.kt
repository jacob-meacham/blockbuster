package com.blockbuster.plugin

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
class NetflixRokuChannelPlugin : RokuChannelPlugin {

    override fun getChannelId(): String = "12"

    override fun getChannelName(): String = "Netflix"

    override fun buildPlaybackCommand(content: RokuMediaContent, rokuDeviceIp: String): RokuPlaybackCommand {
        val contentId = content.contentId
        val mediaType = content.mediaType?.lowercase() ?: "movie"

        return RokuPlaybackCommand.ActionSequence(
            listOf(
                RokuAction.Launch(
                    channelId = getChannelId(),
                    params = "contentId=$contentId&mediaType=$mediaType"
                ),
                RokuAction.Wait(2000),  // Wait for selection page
                RokuAction.Press(RokuKey.PLAY, 1)  // Start playback
            )
        )
    }

    override fun search(query: String): List<RokuMediaContent> {
        // Netflix doesn't provide a public search API
        // Return instructions for manual content ID discovery
        return listOf(
            RokuMediaContent(
                channelName = getChannelName(),
                channelId = getChannelId(),
                contentId = "MANUAL_SEARCH_REQUIRED",
                title = "Manual Search: How to find Netflix content IDs",
                mediaType = "instruction",
                metadata = mapOf(
                    "instructions" to """
                        Netflix does not provide a public search API.

                        To find content IDs manually:

                        1. Open Netflix in your web browser
                        2. Search for and navigate to the content you want
                        3. Look at the URL in your browser's address bar
                        4. Extract the numeric ID from the URL

                        Examples:
                        • URL: https://www.netflix.com/watch/81444554
                        • Content ID: 81444554
                        • Media Type: movie

                        • URL: https://www.netflix.com/watch/80179766?trackId=14170286
                        • Content ID: 80179766
                        • Media Type: episode

                        Use the numeric ID as the contentId when adding to your NFC library.
                        Set mediaType to "movie" or "episode" as appropriate.
                    """.trimIndent(),
                    "searchQuery" to query
                )
            )
        )
    }
}
